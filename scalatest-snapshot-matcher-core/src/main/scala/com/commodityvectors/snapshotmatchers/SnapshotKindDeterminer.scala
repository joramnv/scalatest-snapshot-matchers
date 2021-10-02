package com.commodityvectors.snapshotmatchers

object SnapshotKindDeterminer {

  def isImage(filename: String): Boolean = {
    filename match {
      case filename if filename.endsWith(".png") => true
      case _                                     => false
    }
  }
}
