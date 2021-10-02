package com.commodityvectors.snapshotmatchers

import com.commodityvectors.snapshotmatchers.SnapshotMatcher
import com.commodityvectors.snapshotmatchers.FileReader.readImageFile
import org.scalatest.matchers.should.Matchers.should
import org.scalatest.{Assertion, TestData}

import java.awt.image.BufferedImage

object SnapshotAssertion {

  def assert(filename: String)(implicit matcher: SnapshotMatcher,
                               serializer: SnapshotSerializer[BufferedImage],
                               testData: TestData): Assertion = {
    val result = readImageFile(SnapshotFilename.getResultFilename(filename))
    result should matcher.matchSnapshot(SnapshotFilename.getSnapshotFilename(filename))
  }
}
