package com.commodityvectors.snapshotmatchers

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import scala.io.Source.fromFile

object FileReader {
  def readFile(filename: String): String = {
    val bufferedSource = fromFile(filename)
    val lines = bufferedSource.getLines().mkString("\n")
    bufferedSource.close()
    lines
  }

  def readImageFile(filename: String): BufferedImage = {
    val outputFile = new File(filename)
    ImageIO.read(outputFile)
  }
}
