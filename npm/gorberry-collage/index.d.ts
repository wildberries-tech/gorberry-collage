/**
 * Identifier of an image in the client application
 *
 * The layout engine does not use this value for calculation
 * It is only returned back in the matching tile so the app can connect
 * geometry with its own image model.
 */
export type CollageImageId = string | number;

/**
 * Controls how image content is fitted into calculated tile boxes
 */
export type CollageFitPolicy =
  /**
   * Uses the engine's automatic fitting policy.
   *
   * This mode follows the same rules as TileFitPolicy.Auto in the
   * multiplatform core
   */
  | "auto"

  /**
   * Forces every image to cover its tile
   *
   * This is the default mode for the web API because it matches the usual
   * media-preview behavior: no empty areas inside tiles, with cropping when
   * source image proportions do not match the tile
   */
  | "cover-only";

/**
 * Controls how many layout alternatives the engine checks
 *
 * Higher quality can improve difficult image groups, but costs more CPU
 */
export type CollageQuality =
  /**
   * Checks fewer alternatives. Good for very hot paths or large feeds
   */
  | "fast"

  /**
   * Default quality/speed balance
   */
  | "balanced"

  /**
   * Checks more alternatives. Useful when visual quality matters more
   * than calculation speed
   */
  | "high";

/**
 * The actual image placement mode selected by the engine
 *
 * This is metadata for debugging, analytics or UI decisions.
 * The renderer should still follow imageBox as the source of truth
 */
export type CollageImageFit = "cover" | "contain";

/**
 * Source image metadata
 *
 * The library only needs image dimensions. It does not load, decode,
 * cache or render images
 *
 * Extra fields are allowed by TypeScript structural typing, so product code
 * may keep url, alt, srcSet or any other app specific data on the same object
 */
export type CollageImage<TId extends CollageImageId = CollageImageId> = {
  /**
   * Optional image id from the client app.
   *
   * This may be a backend media id, UUID, database id or any other stable id.
   * If provided, the same value is returned in the corresponding tile.
   */
  id?: TId;

  /**
   * Original image width.
   *
   * The unit does not matter by itself. It can be pixels, points or any other
   * consistent unit. Width and height should be measured in the same unit.
   */
  width: number;

  /**
   * Original image height.
   *
   * The engine uses width/height ratio to plan the collage and image placement
   */
  height: number;
};

/**
 * Layout tuning options.
 *
 * All fields are optional. Defaults are chosen to match the current
 * cross-platform engine configuration
 */
export type CollageOptions = {
  /**
   * Space between neighboring tiles and rows
   *
   * Default: 6
   */
  spacing?: number;

  /**
   * Minimum tile width
   *
   * This helps avoid unreadably narrow image pieces
   *
   * Default: 42
   */
  minTileWidth?: number;

  /**
   * Minimum tile height
   *
   * This helps avoid unreadably short image pieces
   *
   * Default: 42
   */
  minTileHeight?: number;

  /**
   * Maximum number of images in one row
   *
   * Default: 4
   */
  maxTilesPerRow?: number;

  /**
   * Maximum number of landscape-like images in one row
   *
   * This lets the engine avoid rows that look too stretched horizontally
   *
   * Default: 2
   */
  maxLandscapeTilesPerRow?: number;

  /**
   * Quality/speed trade-off
   *
   * Default: "balanced"
   */
  quality?: CollageQuality;

  /**
   * Allows the resulting collage height to exceed maxHeight
   *
   * This is useful when strict height limits would make tiles too small
   * or visually poor
   *
   * Default: false
   */
  allowHeightOverflow?: boolean;

  /**
   * Image fitting policy
   *
   * Use "cover-only" for classic media previews where every tile must be
   * visually filled by the image
   *
   * Use "auto" only when you intentionally want the engine's automatic fitting
   * behavior from the multiplatform core
   *
   * Default: "cover-only"
   */
  fitPolicy?: CollageFitPolicy;
/**
 * Input for collage layout calculation
 */
export type CollageLayoutRequest<TId extends CollageImageId = CollageImageId> = {
  /**
   * Source images in the original order
   *
   * The engine preserves this order when it searches for a good layout
   */
  images: ReadonlyArray<CollageImage<TId>>;

  /**
   * Target collage width
   *
   * All output coordinates use the same unit as this value
   * In web projects this will usually be CSS pixels
   */
  width: number;

  /**
   * Optional minimum collage height
   *
   * Use this when the collage should not become too compressed vertically
   *
   * Default: 0
   */
  minHeight?: number;

  /**
   * Optional maximum collage height.
   *
   * Leave it undefined when there is no maximum height
   * Passing Number.POSITIVE_INFINITY also means "no maximum", but undefined
   * is the preferred
   */
  maxHeight?: number;

  /**
   * Optional layout tuning
   *
   * Most integrations should start with defaults and only set spacing,
   * fitPolicy or maxHeight if the product UI needs it
   */
  options?: CollageOptions;
};

/**
 * Rectangle in collage or tile coordinates
 */
export type CollageRect = {
  /**
   * Horizontal offset
   */
  x: number;

  /**
   * Vertical offset
   */
  y: number;

  /**
   * Rectangle width
   */
  width: number;

  /**
   * Rectangle height
   */
  height: number;
};

/**
 * Geometry for one image in the collage
 */
export type CollageTile<TId extends CollageImageId = CollageImageId> = {
  /**
   * Original image id from request.images, if it was provided
   */
  id?: TId;

  /**
   * Original image index in request.images
   *
   * This is always present and is the safest way to connect a tile
   * with the source image when ids are not used
   */
  imageIndex: number;

  /**
   * Row index in visual order
   */
  rowIndex: number;

  /**
   * Column index inside the row
   */
  columnIndex: number;

  /**
   * Tile rectangle in collage coordinates.
   *
   * Use this rectangle to place the tile/layer container
   */
  box: CollageRect;

  /**
   * Image rectangle relative to tile.box
   *
   * Use this rectangle to place the image inside the tile container
   * do not recompute it with CSS object-fit
   * if you want to preserve the engine's crop minimizing placement
   */
  imageBox: CollageRect;

  /**
   * Actual image fit selected by the engine.
   *
   * This is useful for debugging or analytics. For rendering, imageBox is
   * the source of truth.
   */
  imageFit: CollageImageFit;

  /**
   * Approximate cropped area ratio in range 0..1
   *
   * 0 means no visible crop
   */
  cropRatio: number;

  /**
   * Approximate empty area ratio in range 0..1
   *
   * Usually this is 0 for cover placement and greater than 0 for contain-like
   * placement
   */
  gapRatio: number;
};

/**
 * Calculated collage geometry
 */
export type CollageLayout<TId extends CollageImageId = CollageImageId> = {
  /**
   * Result collage width
   *
   * Normally equals request.width
   */
  width: number;

  /**
   * Result collage height
   *
   * The renderer should use this value as the height of the collage container
   */
  height: number;

  /**
   * Flat list of tiles in visual order.
   *
   * The renderer can map over this array and create one layer per tile
   */
  tiles: Array<CollageTile<TId>>;
};

/**
 * Calculates collage geometry from image dimensions
 *
 * The function does not load images and does not
 * render anything. It only returns rectangles for a renderer.
 */
export function calculateCollageLayout<
  TId extends CollageImageId = CollageImageId,
>(
  request: CollageLayoutRequest<TId>,
): CollageLayout<TId>;
