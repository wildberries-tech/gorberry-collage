//
//  Untitled.swift
//  GorberryCollageSample
//
//  Created by Горбунов Иван Сергеевич on 07.05.2026.
//

import SwiftUI
import GorberryCollage

struct DemoImage {
    let id: Int32
    let assetName: String
    let width: Float
    let height: Float

    func toCollageImage() -> CollageImage {
        CollageImage(
            imageId: id,
            width: width,
            height: height
        )
    }
}

struct DemoCase: Identifiable {
    let id = UUID()
    let title: String
    let description: String
    let images: [DemoImage]
    let minHeight: Float
    let maxHeight: Float?
    let allowHeightOverflow: Bool

    init(
        title: String,
        description: String,
        images: [DemoImage],
        minHeight: Float = 0,
        maxHeight: Float? = nil,
        allowHeightOverflow: Bool = false
    ) {
        self.title = title
        self.description = description
        self.images = images
        self.minHeight = minHeight
        self.maxHeight = maxHeight
        self.allowHeightOverflow = allowHeightOverflow
    }
}

struct WidthVariant: Identifiable {
    let id = UUID()
    let title: String
    let description: String
    let fraction: CGFloat
}

let widthVariants: [WidthVariant] = [
    WidthVariant(
        title: "Full width",
        description: "Uses the whole available message width.",
        fraction: 1.00
    ),
    WidthVariant(
        title: "Medium bubble",
        description: "Simulates a regular chat bubble width.",
        fraction: 0.78
    ),
    WidthVariant(
        title: "Compact bubble",
        description: "Simulates a narrow message container.",
        fraction: 0.58
    ),
]

private let dog = DemoImage(id: 1, assetName: "demo_dog", width: 1122, height: 1402)
private let cats = DemoImage(id: 2, assetName: "demo_cats", width: 1536, height: 1024)
private let macaw = DemoImage(id: 3, assetName: "demo_macaw", width: 1254, height: 1254)
private let horses = DemoImage(id: 4, assetName: "demo_horses", width: 1672, height: 941)
private let sportCar = DemoImage(id: 5, assetName: "demo_sport_car", width: 1536, height: 1024)
private let van = DemoImage(id: 6, assetName: "demo_van", width: 1122, height: 1402)
private let suv = DemoImage(id: 7, assetName: "demo_suv", width: 1254, height: 1254)
private let giraffe = DemoImage(id: 8, assetName: "demo_giraffe", width: 1122, height: 1402)

private let ultraWide = DemoImage(id: 9, assetName: "demo_ultra_wide", width: 2400, height: 600)
private let ultraTall = DemoImage(id: 10, assetName: "demo_ultra_tall", width: 600, height: 2400)
private let panorama = DemoImage(id: 11, assetName: "demo_panorama", width: 2600, height: 900)
private let vertical = DemoImage(id: 12, assetName: "demo_vertical", width: 700, height: 2200)

private let demoImagePool: [DemoImage] = [
    dog,
    cats,
    macaw,
    horses,
    sportCar,
    van,
    suv,
    giraffe,
    ultraWide,
    ultraTall,
    panorama,
    vertical,
]

private func repeatedDemoImages(_ count: Int) -> [DemoImage] {
    (0..<count).map { index in
        demoImagePool[index % demoImagePool.count]
    }
}

let demoCases: [DemoCase] = [
    DemoCase(
        title: "Tall image pair",
        description: "Two portrait-shaped images in a compact media group.",
        images: [
            dog,
            giraffe,
        ]
    ),
    DemoCase(
        title: "Mixed orientations",
        description: "Landscape, square, and portrait images in one message.",
        images: [
            cats,
            macaw,
            dog,
        ]
    ),
    DemoCase(
        title: "Four-tile media preview",
        description: "Wide, tall, square, and landscape content in one group.",
        images: [
            horses,
            van,
            macaw,
            sportCar,
        ]
    ),
    DemoCase(
        title: "Chat attachment group",
        description: "Five images arranged as a message-style attachment preview.",
        images: [
            dog,
            cats,
            macaw,
            horses,
            van,
        ]
    ),
    DemoCase(
        title: "Vehicle media group",
        description: "Different vehicle shots with square, portrait, and landscape ratios.",
        images: [
            sportCar,
            van,
            suv,
        ]
    ),
    DemoCase(
        title: "Extreme aspect ratios",
        description: "Ultra-wide and ultra-tall images mixed with regular content.",
        images: [
            ultraWide,
            ultraTall,
            sportCar,
            dog,
        ]
    ),
    DemoCase(
        title: "Ultra stress test",
        description: "Panoramic and vertical images mixed together in one preview.",
        images: [
            panorama,
            vertical,
            ultraWide,
            ultraTall,
            macaw,
        ]
    ),
    DemoCase(
        title: "Height-limited group",
        description: "A larger media group planned under a maximum layout height.",
        images: [
            dog,
            cats,
            macaw,
            horses,
            sportCar,
            giraffe,
        ],
        maxHeight: 420
    ),
    DemoCase(
        title: "Long media group with overflow",
        description: "Twenty images with maxHeight set. Overflow is allowed to preserve readable tiles.",
        images: repeatedDemoImages(20),
        maxHeight: 520,
        allowHeightOverflow: true
    ),
]
