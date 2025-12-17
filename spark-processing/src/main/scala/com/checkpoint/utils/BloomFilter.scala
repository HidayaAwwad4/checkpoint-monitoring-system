package com.checkpoint.utils

import java.util.BitSet
import scala.util.hashing.MurmurHash3

class BloomFilter(expectedElements: Int, falsePositiveRate: Double = 0.01) extends Serializable {


  private val numBits: Int = optimalNumBits(expectedElements, falsePositiveRate)

  private val numHashFunctions: Int = optimalNumHashFunctions(expectedElements, numBits)

  private val bitSet: BitSet = new BitSet(numBits)

  private var elementCount: Int = 0

  def add(item: String): Unit = {
    val hashes = getHashes(item)
    hashes.foreach { hash =>
      bitSet.set(Math.abs(hash % numBits))
    }
    elementCount += 1
  }

  def mightContain(item: String): Boolean = {
    val hashes = getHashes(item)
    hashes.forall { hash =>
      bitSet.get(Math.abs(hash % numBits))
    }
  }

  private def getHashes(item: String): Seq[Int] = {
    (1 to numHashFunctions).map { i =>
      MurmurHash3.stringHash(item, i)
    }
  }

  private def optimalNumBits(n: Int, p: Double): Int = {
    val m = -1 * n * math.log(p) / math.pow(math.log(2), 2)
    math.ceil(m).toInt
  }

  private def optimalNumHashFunctions(n: Int, m: Int): Int = {
    val k = (m.toDouble / n) * math.log(2)
    math.ceil(k).toInt
  }

  def getStats: String = {
    s"""
       |Bloom Filter Statistics:
       |------------------------
       |Expected Elements: $expectedElements
       |False Positive Rate: ${falsePositiveRate * 100}%
       |Bit Array Size: $numBits bits (${numBits / 8} bytes)
       |Number of Hash Functions: $numHashFunctions
       |Elements Added: $elementCount
       |Fill Ratio: ${(elementCount.toDouble / expectedElements * 100).formatted("%.2f")}%
       |""".stripMargin
  }

  def clear(): Unit = {
    bitSet.clear()
    elementCount = 0
  }
}

object BloomFilter {

  def apply(expectedElements: Int = 10000, falsePositiveRate: Double = 0.01): BloomFilter = {
    new BloomFilter(expectedElements, falsePositiveRate)
  }
}