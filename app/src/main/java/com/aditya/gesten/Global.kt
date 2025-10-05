package com.aditya.gesten

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Gesture{
    var leftAngleX : Double = 0.0
    var rightAngleX : Double = 0.0

    var leftAngleY : Double = 0.0
    var rightAngleY : Double = 0.0

    var leftHand : Array<Double> = Array<Double>(5){0.0}
    var rightHand : Array<Double> = Array<Double>(5){0.0}

}


const val n_read = 16
val angle_indices = listOf(5,6,12,13)

var index_read by mutableStateOf(arrayOf(0,0))

var hand_data by mutableStateOf(Array(14) { FloatArray(n_read) })
var bluetoothStatus by mutableStateOf(arrayOf(0,0))
var displayText by mutableStateOf("")


fun predict(): String{
    if(range.isEmpty()) return ""

    return manualPredict()
}


fun manualPredict(): String {
    val f0 = (hand_data[0].average()-min[0])/range[0]
    val f1 = (hand_data[1].average()-min[1])/range[1]
    val f2 = (hand_data[2].average()-min[2])/range[2]
    val f3 = (hand_data[3].average()-min[3])/range[3]
    val f4 = (hand_data[4].average()-min[4])/range[4]
    val lx = (hand_data[5].average()-min[5])/range[5]
    val ly = (hand_data[6].average()-min[6])/range[6]
    val f5 = (hand_data[7].average()-min[7])/range[7]
    val f6 = (hand_data[8].average()-min[8])/range[8]
    val f7 = (hand_data[9].average()-min[9])/range[9]
    val f8 = (hand_data[10].average()-min[10])/range[10]
    val f9 = (hand_data[11].average()-min[11])/range[11]
    val rx = (hand_data[12].average()-min[12])/range[12]
    val ry = (hand_data[13].average()-min[13])/range[13]
//    val fmin = listOf(f5, f6, f7, f8,f9).min()
//    val fmax = listOf(f5, f6, f7, f8,f9).min()
//    if((fmax -fmin) > .5){
//        thr = (fmin + fmax)/2
//    }

    var mode = 0
    if(ly > .5)mode += 1
    if(ry > .5)mode += 2

    //NULL
    if(mode == 0) return ""


    //BOTH
    if(mode == 3) {
        if(ry>.7 && ly > .7) return "namaste"
        return "name is"
    }


    var thr = .8


    //LEFT
    if(mode == 1) {
        return if(ly < .7){
            if(lx > 1 || lx < 0)
                "thank you"
            else
                "aditya"
        }
        else {
            "goodbye"
        }
    }

    //RIGHT
    if(mode == 2){
        return if(ry < .7){
            if(rx > .38 && rx < .62)
                "jai hind"
            else "my"
        } else{
//            if(f5<thr &&f6<thr &&f7<thr &&f8<thr &&f9<thr){
                "hi"
//            } else{
//                "adi"
//            }

        }
    }

    return "?? ${mode.toString()}"
}

fun getGestureFromData(): Gesture{

    val g = Gesture()
    g.leftHand = arrayOf(hand_data[0].average(),hand_data[1].average(),hand_data[2].average(),hand_data[3].average(),hand_data[4].average())
    g.rightHand = arrayOf(hand_data[7].average(),hand_data[8].average(),hand_data[9].average(),hand_data[10].average(),hand_data[11].average())


    g.leftAngleX = hand_data[5].average()
    g.leftAngleY = hand_data[6].average()

    g.rightAngleX = hand_data[5].average()
    g.rightAngleY = hand_data[6].average()

    return g

}

const val range_threshold = 20
const val range_threshold_max = 60
const val sample_cycles = 8
var cycles = arrayOf(sample_cycles-2,sample_cycles-2)

var range: Array<Double> = arrayOf()
var min:Array<Double> = arrayOf()
var max:Array<Double> = arrayOf()

var candidate_range: Array<Double> = arrayOf()
var candidate_min:Array<Double> = arrayOf()
var candidate_max:Array<Double> = arrayOf()


fun nameyesy(n:Int){
    if(index_read[n] == 0){
        if(candidate_min.isEmpty()){
            candidate_min = hand_data.map { e -> e.average() }.toTypedArray()
            candidate_max = candidate_min.clone()
            candidate_range = hand_data.map { e -> 1.0 }.toTypedArray()
        }
        else{
            for(i in n*7 until (n+1)*7){
                val d = hand_data[i].average()
                if(d < candidate_min[i]){
                    candidate_min[i] = d
                }
                if(d > candidate_max[i]){
                    candidate_max[i] = d
                }
                candidate_range[i] = kotlin.math.max(candidate_max[i] - candidate_min[i],1.0)
            }
        }

        if(cycles[n] >sample_cycles) {
            for(i in n*7 until (n+1)*7){
                if(i in angle_indices)continue

                if(candidate_range[i] > range_threshold_max){
                    Log.e("DO", "$i ${candidate_range[i]}")
                    candidate_min[i] = hand_data[i].average()
                    candidate_max[i] = hand_data[i].average()
                    candidate_range[i] = 1.0
                    break
                }
                if (candidate_range[i] < range_threshold) {
                    Log.e("DO", "$i ${candidate_range[i]}")
                    break
                }
                min[i] = candidate_min[i]
                max[i] = candidate_max[i]
                range[i] = candidate_range[i]
                candidate_min[i] = hand_data[i].average()
                candidate_range[i] = 1.0
                candidate_max[i] = candidate_min[i]

            }

            cycles[n] = 0
            for(i in n*7 until (n+1)*7){
                val d = hand_data[i].average()
                candidate_min[i] = d
                candidate_max[i] = d
                candidate_range[i] = kotlin.math.max(candidate_max[i] - candidate_min[i],1.0)
            }
        }
        else{
            Log.e("cycle", cycles.toString())
        }
        cycles[n]+=1
    }
}

fun calibrate(g:Gesture){
    val values = DoubleArray(14)
    var idx = 0

    g.leftHand.forEach { values[idx++] = it.toDouble() }
    values[idx++] = g.leftAngleX
    values[idx++] = g.leftAngleY
    g.rightHand.forEach { values[idx++] = it.toDouble() }
    values[idx++] = g.rightAngleX
    values[idx++] = g.rightAngleY


    if(min.isEmpty()){
        min = values.toTypedArray()
        max = values.toTypedArray()
        range = values.toTypedArray()
    }


    values.forEachIndexed { i,v ->
        if(v > max[i]){
            max[i] = v
        }
        if( v < min[i]){
            min[i] = v
        }
    }

    for(a in angle_indices){
        min[a] = -90.0
        max[a] = 90.0
    }

    for(i in 0 until max.size){
        range[i] = max[i] - min[i]
        if(range[i].toInt() == 0) range[i] = 1.0
    }

}

fun setNormalizationVariables(gesturesData: ArrayList<Gesture>){
    var f = true
    lateinit var max: Array<Double>
    for (g in gesturesData) {
        val values = DoubleArray(7)
        var idx = 0
        g.leftHand.forEach { values[idx++] = it.toDouble() }
        if(f){
            min = values.toTypedArray()
            max = values.toTypedArray()
            f = false
        }

        values[5] = g.leftAngleX
        values[6] = g.leftAngleY

        values.forEachIndexed { i,v ->
            if(v > max[i]){
                max[i] = v
            }
            if( v < min[i]){
                min[i] = v
            }
        }

        range = min.clone()
        for(i in 0 until max.size){
            range[i] = max[i] - min[i]
            if(range[i].toInt() == 0) range[i] = 1.0
        }
    }

}