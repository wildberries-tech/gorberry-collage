//
//  CollageRenderer.swift
//  GorberryCollageSample
//
//  Created by Горбунов Иван Сергеевич on 07.05.2026.
//

import SwiftUI
import GorberryCollage

struct CollageLayoutView: View {
    let layout: CollageLayout
    let imagesById: [Int32: UIImage]
    let debugOverlay: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(allTiles.enumerated()), id: \.offset) { _, tile in
                if let image = imagesById[tile.imageId] {
                    CollageTileView(
                        tile: tile,
                        image: image,
                        debugOverlay: debugOverlay
                    )
                }
            }
        }
        .frame(
            width: CGFloat(layout.width),
            height: CGFloat(layout.height),
            alignment: .topLeading
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var allTiles: [CollageTile] {
        let rows = layout.rows as? [CollageRow] ?? []
        return rows.flatMap { row in
            row.tiles as? [CollageTile] ?? []
        }
    }
}

struct CollageTileView: View {
    let tile: CollageTile
    let image: UIImage
    let debugOverlay: Bool

    var body: some View {
        let box = tile.box
        let contentBox = tile.contentBox

        ZStack(alignment: .topLeading) {
            Image(uiImage: image)
                .resizable()
                .frame(
                    width: CGFloat(contentBox.width),
                    height: CGFloat(contentBox.height)
                )
                .offset(
                    x: CGFloat(contentBox.x - box.x),
                    y: CGFloat(contentBox.y - box.y)
                )

            if debugOverlay {
                Text(tile.debugText)
                    .font(.caption2)
                    .padding(4)
                    .background(Color.black.opacity(0.55))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .padding(4)
                    .frame(
                        width: CGFloat(box.width),
                        height: CGFloat(box.height),
                        alignment: .bottomLeading
                    )
            }
        }
        .frame(
            width: CGFloat(box.width),
            height: CGFloat(box.height),
            alignment: .topLeading
        )
        .clipped()
        .overlay {
            if debugOverlay {
                Rectangle()
                    .stroke(Color.blue, lineWidth: 1)
            }
        }
        .position(
            x: CGFloat(box.x + box.width / 2),
            y: CGFloat(box.y + box.height / 2)
        )
    }
}

private extension CollageTile {
    var debugText: String {
        let fitName = String(describing: fit)
        let isContain = fitName.localizedCaseInsensitiveContains("CONTAIN")
        let metric = isContain ? containGapRatio : cropRatio
        let metricName = isContain ? "gap" : "crop"

        return "\(fitName) \(metricName)=\(Int((metric * 100).rounded()))%"
    }

    var containGapRatio: Float {
        let boxArea = max(box.width * box.height, 1)
        let contentArea = max(contentBox.width * contentBox.height, 0)
        return min(max(1 - contentArea / boxArea, 0), 1)
    }
}
