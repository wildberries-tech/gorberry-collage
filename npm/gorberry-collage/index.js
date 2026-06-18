import {
  RawCollageImage,
  __calculateCollageLayoutRaw,
} from "./dist/kotlin/gorberry-collage.mjs";

const DEFAULT_OPTIONS = Object.freeze({
  spacing: 6,
  minTileWidth: 42,
  minTileHeight: 42,
  maxTilesPerRow: 4,
  maxLandscapeTilesPerRow: 2,
  quality: "balanced",
  allowHeightOverflow: false,
  fitPolicy: "cover-only",
});

const QUALITY_VALUES = new Set(["fast", "balanced", "high"]);
const FIT_POLICY_VALUES = new Set(["auto", "cover-only"]);

/**
 * Calculates collage geometry for the given image sizes.
 *
 * This function does not load images, does not create DOM nodes,
 * and does not render anything. It only returns geometry.
 *
 * @param {import("./index.d.ts").CollageLayoutRequest} request
 * @returns {import("./index.d.ts").CollageLayout}
 */
export function calculateCollageLayout(request) {
  assertPlainObject(request, "request");

  if (!Array.isArray(request.images)) {
    throw new TypeError("request.images must be an array");
  }

  if (request.images.length === 0) {
    throw new RangeError("request.images must not be empty");
  }

  const width = readPositiveFiniteNumber(request.width, "request.width");

  const minHeight = readOptionalNonNegativeFiniteNumber(
    request.minHeight,
    "request.minHeight",
    0,
  );

  const maxHeight = readOptionalMaxHeight(
    request.maxHeight,
    "request.maxHeight",
    Number.POSITIVE_INFINITY,
  );

  if (minHeight > maxHeight) {
    throw new RangeError("request.minHeight must be <= request.maxHeight");
  }

  const options = normalizeOptions(request.options);

  const rawImages = request.images.map((image, index) => {
    assertPlainObject(image, `request.images[${index}]`);
    validateImageId(image.id, `request.images[${index}].id`);

    return new RawCollageImage(
      index,
      readPositiveFiniteNumber(image.width, `request.images[${index}].width`),
      readPositiveFiniteNumber(image.height, `request.images[${index}].height`),
    );
  });

  const rawLayout = __calculateCollageLayoutRaw(
    rawImages,

    width,
    minHeight,
    maxHeight,

    options.spacing,
    options.minTileWidth,
    options.minTileHeight,
    options.maxTilesPerRow,
    options.maxLandscapeTilesPerRow,
    options.quality,
    options.allowHeightOverflow,
    options.fitPolicy,
  );

  return {
    width: rawLayout.width,
    height: rawLayout.height,
    tiles: Array.from(rawLayout.tiles, (rawTile) =>
      toPublicTile(rawTile, request.images),
    ),
  };
}

function normalizeOptions(options) {
  if (options === undefined) {
    return DEFAULT_OPTIONS;
  }

  assertPlainObject(options, "request.options");

  const maxTilesPerRow = readOptionalInteger(
    options.maxTilesPerRow,
    "request.options.maxTilesPerRow",
    DEFAULT_OPTIONS.maxTilesPerRow,
    { min: 1 },
  );

  const maxLandscapeTilesPerRow = readOptionalInteger(
    options.maxLandscapeTilesPerRow,
    "request.options.maxLandscapeTilesPerRow",
    DEFAULT_OPTIONS.maxLandscapeTilesPerRow,
    { min: 0 },
  );

  if (maxLandscapeTilesPerRow > maxTilesPerRow) {
    throw new RangeError(
      "request.options.maxLandscapeTilesPerRow must be <= request.options.maxTilesPerRow",
    );
  }

  return {
    spacing: readOptionalNonNegativeFiniteNumber(
      options.spacing,
      "request.options.spacing",
      DEFAULT_OPTIONS.spacing,
    ),
    minTileWidth: readOptionalPositiveFiniteNumber(
      options.minTileWidth,
      "request.options.minTileWidth",
      DEFAULT_OPTIONS.minTileWidth,
    ),
    minTileHeight: readOptionalPositiveFiniteNumber(
      options.minTileHeight,
      "request.options.minTileHeight",
      DEFAULT_OPTIONS.minTileHeight,
    ),
    maxTilesPerRow,
    maxLandscapeTilesPerRow,
    quality: readOptionalEnum(
      options.quality,
      "request.options.quality",
      DEFAULT_OPTIONS.quality,
      QUALITY_VALUES,
    ),
    allowHeightOverflow: readOptionalBoolean(
      options.allowHeightOverflow,
      "request.options.allowHeightOverflow",
      DEFAULT_OPTIONS.allowHeightOverflow,
    ),
    fitPolicy: readOptionalEnum(
      options.fitPolicy,
      "request.options.fitPolicy",
      DEFAULT_OPTIONS.fitPolicy,
      FIT_POLICY_VALUES,
    ),
  };
}

function toPublicTile(rawTile, images) {
  const sourceImage = images[rawTile.imageIndex];

  const tile = {
    imageIndex: rawTile.imageIndex,
    rowIndex: rawTile.rowIndex,
    columnIndex: rawTile.columnIndex,

    box: {
      x: rawTile.x,
      y: rawTile.y,
      width: rawTile.width,
      height: rawTile.height,
    },

    imageBox: {
      x: rawTile.imageX,
      y: rawTile.imageY,
      width: rawTile.imageWidth,
      height: rawTile.imageHeight,
    },

    imageFit: rawTile.imageFit,
    cropRatio: rawTile.cropRatio,
    gapRatio: rawTile.gapRatio,
  };

  if (
    sourceImage !== undefined &&
    Object.prototype.hasOwnProperty.call(sourceImage, "id") &&
    sourceImage.id !== undefined
  ) {
    tile.id = sourceImage.id;
  }

  return tile;
}

function assertPlainObject(value, name) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${name} must be an object`);
  }
}

function validateImageId(value, name) {
  if (value === undefined) {
    return;
  }

  if (typeof value === "string") {
    return;
  }

  if (typeof value === "number" && Number.isFinite(value)) {
    return;
  }

  throw new TypeError(`${name} must be a string or a finite number`);
}

function readPositiveFiniteNumber(value, name) {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    throw new RangeError(`${name} must be a finite number > 0`);
  }

  return value;
}

function readOptionalPositiveFiniteNumber(value, name, fallback) {
  if (value === undefined) {
    return fallback;
  }

  return readPositiveFiniteNumber(value, name);
}

function readOptionalNonNegativeFiniteNumber(value, name, fallback) {
  if (value === undefined) {
    return fallback;
  }

  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
    throw new RangeError(`${name} must be a finite number >= 0`);
  }

  return value;
}

function readOptionalMaxHeight(value, name, fallback) {
  if (value === undefined) {
    return fallback;
  }

  if (value === Number.POSITIVE_INFINITY) {
    return value;
  }

  return readPositiveFiniteNumber(value, name);
}

function readOptionalInteger(value, name, fallback, { min }) {
  if (value === undefined) {
    return fallback;
  }

  if (!Number.isInteger(value) || value < min) {
    throw new RangeError(`${name} must be an integer >= ${min}`);
  }

  return value;
}

function readOptionalBoolean(value, name, fallback) {
  if (value === undefined) {
    return fallback;
  }

  if (typeof value !== "boolean") {
    throw new TypeError(`${name} must be a boolean`);
  }

  return value;
}

function readOptionalEnum(value, name, fallback, allowedValues) {
  if (value === undefined) {
    return fallback;
  }

  if (typeof value !== "string" || !allowedValues.has(value)) {
    throw new TypeError(
      `${name} must be one of: ${Array.from(allowedValues).join(", ")}`,
    );
  }

  return value;
}
