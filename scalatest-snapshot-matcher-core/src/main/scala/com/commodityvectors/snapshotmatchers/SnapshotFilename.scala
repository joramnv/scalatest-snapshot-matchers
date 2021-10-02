package com.commodityvectors.snapshotmatchers

object SnapshotFilename {

  def getSnapshotFilename(filename: String): String =
    filename.toLowerCase

  def getResultFilename(filename: String): String =
    s"./target/test-output/${getSnapshotFilename(filename)}"
}
