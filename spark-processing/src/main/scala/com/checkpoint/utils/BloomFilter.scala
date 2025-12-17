package com.checkpoint.utils

import java.util.BitSet
import scala.util.hashing.MurmurHash3

class BloomFilter(expectedElements: Int, falsePositiveRate: Double = 0.01) extends Serializable {


  private val numBits: Int = optimalNumBits(expectedElements, falsePositiveRate)

  private val numHashFunctions: Int = optimalNumHashFunctions(expectedElements, numBits)

  private val bitSet: BitSet = new BitSet(numBits)

  private var elementCount: Int = 0
