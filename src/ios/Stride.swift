//
//  Stride.swift
//
//  Created by Leo on 3/6/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//

struct Stride {
    enum orders {
        case MaxMinMax
        case MinMaxMin
        case none
    }
    
    let heightMax: Double
    let heightMin: Double
    let amplitude: Double
    let lengthFirst: Int
    let lengthSecond: Int
    let lengthTotal: Int
    let axis: Int
    let strideType: orders
    
    init() {
        self.heightMax = 0
        self.heightMin = 0
        self.amplitude = 0
        self.lengthFirst = 0
        self.lengthSecond = 0
        self.lengthTotal = 0
        self.axis = 0
        self.strideType = .none
    }
    
    init(_ heightMax: Double, _ heightMin: Double, _ lengthFirst: Int, _ lengthSecond: Int, _ axis: Int, _ strideType: orders) {
        self.heightMax = heightMax
        self.heightMin = heightMin
        self.amplitude = heightMax - heightMin
        self.lengthFirst = lengthFirst
        self.lengthSecond = lengthSecond
        self.lengthTotal = lengthFirst + lengthSecond
        self.axis = axis
        self.strideType = strideType
    }
    
    init(_ amplitude: Double, _ lengthTotal: Int, _ axis: Int, _ strideType: orders) {
        self.heightMax = 0
        self.heightMin = 0
        self.amplitude = amplitude
        self.lengthFirst = 0
        self.lengthSecond = 0
        self.lengthTotal = lengthTotal
        self.axis = axis
        self.strideType = strideType
    }
}
