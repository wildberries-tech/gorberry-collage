//
//  ContentView.swift
//  GorberryCollageSample
//
//  Created by Горбунов Иван Сергеевич on 07.05.2026.
//

import SwiftUI
import GorberryCollage

struct ContentView: View {
    @State private var zeroSpacing = false
    @State private var coverOnly = true
    @State private var debugOverlay = false

    var body: some View {
        GeometryReader { proxy in
            let availableWidth = max(proxy.size.width - 32 - 24, 1)

            let normalEngine = makeCollageEngine(
                zeroSpacing: zeroSpacing,
                coverOnly: coverOnly,
                allowHeightOverflow: false
            )

            let overflowEngine = makeCollageEngine(
                zeroSpacing: zeroSpacing,
                coverOnly: coverOnly,
                allowHeightOverflow: true
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header

                    ForEach(demoCases) { demoCase in
                        DemoFeedMessage(
                            engine: demoCase.allowHeightOverflow ? overflowEngine : normalEngine,
                            demoCase: demoCase,
                            availableWidth: availableWidth,
                            debugOverlay: debugOverlay
                        )
                    }
                }
                .padding(16)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Gorberry Collage")
                .font(.largeTitle)
                .bold()

            Toggle("Zero spacing", isOn: $zeroSpacing)
            Toggle("Cover only", isOn: $coverOnly)
            Toggle("Debug overlay", isOn: $debugOverlay)
        }
    }
}

struct DemoFeedMessage: View {
    let engine: CollageEngine
    let demoCase: DemoCase
    let availableWidth: CGFloat
    let debugOverlay: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(demoCase.title)
                .font(.headline)

            Text(demoCase.description)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            if demoCase.allowHeightOverflow {
                Text("Height overflow is enabled for this case.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach(widthVariants) { widthVariant in
                DemoWidthVariantMessage(
                    engine: engine,
                    demoCase: demoCase,
                    widthVariant: widthVariant,
                    availableWidth: availableWidth,
                    debugOverlay: debugOverlay
                )
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct DemoWidthVariantMessage: View {
    let engine: CollageEngine
    let demoCase: DemoCase
    let widthVariant: WidthVariant
    let availableWidth: CGFloat
    let debugOverlay: Bool

    var body: some View {
        let bubbleWidth = min(
            max(140, availableWidth * widthVariant.fraction),
            availableWidth
        )
        let innerPadding: CGFloat = 8
        let layoutWidth = max(1, bubbleWidth - innerPadding * 2)

        let layout = calculateLayout(
            engine: engine,
            demoCase: demoCase,
            width: Float(layoutWidth)
        )

        VStack(alignment: .leading, spacing: 6) {
            Text(widthVariant.title)
                .font(.caption)
                .bold()

            Text(widthVariant.description)
                .font(.caption2)
                .foregroundStyle(.secondary)

            HStack {
                Spacer()

                VStack(alignment: .leading, spacing: 6) {
                    Text(metadataText(layout: layout, width: layoutWidth))
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    CollageLayoutView(
                        layout: layout,
                        imagesById: imagesById,
                        debugOverlay: debugOverlay
                    )
                }
                .padding(innerPadding)
                .frame(width: bubbleWidth, alignment: .leading)
                .background(Color(.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    private var imagesById: [Int32: UIImage] {
        var result: [Int32: UIImage] = [:]

        for image in demoCase.images {
            if result[image.id] == nil {
                result[image.id] = UIImage(named: image.assetName)
            }
        }

        return result
    }

    private func calculateLayout(
        engine: CollageEngine,
        demoCase: DemoCase,
        width: Float
    ) -> CollageLayout {
        let collageImages = demoCase.images.map { image in
            image.toCollageImage()
        }

        let maxHeight = demoCase.maxHeight ?? defaultMessageMaxHeight(for: width)

        return engine.layout(
            images: collageImages,
            width: width,
            minHeight: demoCase.minHeight,
            maxHeight: maxHeight
        )
    }

    private func defaultMessageMaxHeight(for width: Float) -> Float {
        min(
            max(width * 1.35, 220),
            520
        )
    }

    private func metadataText(
        layout: CollageLayout,
        width: CGFloat
    ) -> String {
        var parts: [String] = []

        let effectiveMaxHeight = demoCase.maxHeight ?? defaultMessageMaxHeight(for: Float(width))

        parts.append("\(demoCase.images.count) images")
        parts.append("width=\(Int(width.rounded()))pt")
        parts.append("height=\(Int(layout.height.rounded()))pt")
        parts.append("maxHeight=\(Int(effectiveMaxHeight.rounded()))pt")

        if demoCase.allowHeightOverflow {
            parts.append("overflow allowed")
        }

        return parts.joined(separator: ", ")
    }
}
