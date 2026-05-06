//
//  CollageEngineFactory.swift
//  GorberryCollageSample
//
//  Created by Горбунов Иван Сергеевич on 07.05.2026.
//

import Foundation
import GorberryCollage

func makeCollageEngine(
    zeroSpacing: Bool,
    coverOnly: Bool,
    allowHeightOverflow: Bool
) -> CollageEngine {
    let configuration = CollageEngine.Configuration()

    configuration.spacing = zeroSpacing ? 0 : 2
    configuration.minTileWidth = 42
    configuration.minTileHeight = 42
    configuration.maxTilesPerRow = 4
    configuration.maxLandscapeTilesPerRow = 2
    configuration.allowHeightOverflow = allowHeightOverflow

    configuration.tileFitPolicy = coverOnly ? TileFitPolicy.coveronly : TileFitPolicy.auto_

    return CollageEngine(configuration: configuration)
}
