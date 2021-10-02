package com.commodityvectors.snapshotmatchers

import com.typesafe.config.ConfigFactory
import difflib.DiffUtils
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{FixtureTestSuite, Outcome, SuiteMixin, TestData}

import java.awt.image.BufferedImage
import java.io.{File, PrintWriter}
import java.nio.file.Paths
import javax.imageio.ImageIO
import scala.collection.JavaConverters.*
import scala.io.Source
import scala.util.Try

private object SnapshotLoader {
  val DefaultSnapshotFolder = "src/test/__snapshots__"
}

trait SnapshotLoader {
  import SnapshotLoader.*

  private val testFolder = getClass.getName.replaceAll("\\.", "/").toLowerCase

  private val fullWritePath =
    s"${getClass.getResource("").getPath.split("target").head}$DefaultSnapshotFolder/$testFolder"

  private def folderPath: String = new File(s"$fullWritePath").getAbsolutePath
  private def filePath(filename: String): String = new File(s"$folderPath/$filename").getAbsolutePath
  private def filename(id: String): String = if (id.contains(".")) then id else s"$id.snap"

  def loadSnapshot(id: String): Option[Any] = {
    val filename = this.filename(id)
    val filePath = this.filePath(filename)
    if (SnapshotKindDeterminer.isImage(filePath)) {
      Try(FileReader.readImageFile(filePath)).toOption
    } else {
      Try(Source.fromFile(filePath).mkString).toOption
    }
  }

  def writeSnapshot[T](id: String, content: T)(implicit s: SnapshotSerializer[T]): Unit = {
    new File(folderPath).mkdirs()

    val filename = this.filename(id)
    val filePath = this.filePath(filename)
    val file = new File(filePath)

    if (SnapshotKindDeterminer.isImage(filename)) {
      ImageIO.write(content.asInstanceOf[BufferedImage], "png", file)
    } else {
      new PrintWriter(file) {
        write(s.serialize(content))
        close()
      }
    }
  }
}

trait TestDataArgs extends SuiteMixin { this: FixtureTestSuite =>
  type FixtureParam = TestData

  override def withFixture(test: OneArgTest): Outcome = {
    withFixture(test.toNoArgTest(test))
  }
}

trait TestDataEnhancer {
  implicit class TestDataEnhancer(in: TestData) {
    def key: String = in.name.replaceAll("[^A-Za-z0-9]", "-").toLowerCase()
  }
}

trait SnapshotMessages {
  val ContentsAreEqual = "Contents Are Equal"
  val DefaultError = "Contents Are Different"

  def snapshotPreventedError(key: String) = s"Snapshot [$key] was not generated due to Environment flag."

  def errorMessage(current: String, found: String): String = {
    val patch = DiffUtils.diff(found.split("\n").toList.asJava, current.split("\n").toList.asJava)
    val diff = DiffUtils
      .generateUnifiedDiff("Original Snapshot", "New Snapshot", found.split("\n").toList.asJava, patch, 10)
      .asScala
    val parsedLines = diff.map { line =>
      if (line.startsWith("+"))
        s"${Console.GREEN}$line${Console.RESET}"
      else if (line.startsWith("-"))
        s"${Console.RED}$line${Console.RESET}"
      else
        s"${Console.WHITE}$line${Console.RESET}"
    }

    s"""|Text Did not match:
        |${parsedLines.mkString("\n")}
        |
        |End Diff;""".stripMargin
  }
}

trait SnapshotMatcher extends SnapshotLoader with SnapshotMessages with TestDataArgs with DefaultSerializers {
  self: FixtureTestSuite =>

  private var testMap: Map[String, Int] = Map.empty
  private val ShouldGenerateSnapshot = sys.env.get("updateSnapshots").getOrElse("false").toBoolean

  private def getCurrentAndSetNext(id: String, isExplicit: Boolean): String = {
    val next = testMap.getOrElse(id, 0) + 1
    testMap += (id -> next)
    val current = next - 1
    if (current == 0) id
    else if (!isExplicit) s"$id-$current"
    else
      throw new Exception("Cannot reuse snapshot for explicit identifier. There should be only a single snapshot match")
  }

  class SnapshotShouldMatch[T](explicitId: Option[String])(implicit s: SnapshotSerializer[T], test: TestData)
      extends Matcher[T]
      with TestDataEnhancer {
    override def apply(left: T): MatchResult = {
      val testIdentifier = getCurrentAndSetNext(explicitId.getOrElse(test.key), isExplicit = explicitId.nonEmpty)
      loadSnapshot(testIdentifier) match {
        case Some(content) if isEqual(content, left) =>
          MatchResult(matches = true, DefaultError, ContentsAreEqual)
        case Some(_) if ShouldGenerateSnapshot =>
          println(s"${Console.YELLOW} ### Updating Snapshots: ${test.name} ###")
          writeSnapshot(testIdentifier, left)
          MatchResult(matches = true, DefaultError, ContentsAreEqual)
        case Some(content) =>
          MatchResult(matches = false,
                      errorMessage(s.serialize(left),
                                   content.toString /* TODO should write this better than just adding .toString. */ ),
                      ContentsAreEqual)
        case None => // first time / new test
          writeSnapshot(testIdentifier, left)
          MatchResult(matches = true, DefaultError, ContentsAreEqual)
      }
    }

    private def isEqual(content: Any, left: T): Boolean = {
      if (content.isInstanceOf[BufferedImage]) then isEqual (content.asInstanceOf[BufferedImage], left)
      else if (content.isInstanceOf[String]) then isEqual (content.asInstanceOf[String], left)
      else false
    }

    private def isEqual(content: String, left: T): Boolean = s.serialize(left) == content

    private def isEqual(content: BufferedImage, left: T): Boolean = {
      if (left.isInstanceOf[BufferedImage]) then compareImages (content, left.asInstanceOf[BufferedImage])
      else false
    }

    def compareImages(imgA: BufferedImage, imgB: BufferedImage): Boolean = {
      // The images must be the same size.
      if (imgA.getWidth != imgB.getWidth || imgA.getHeight != imgB.getHeight) return false
      val width = imgA.getWidth
      val height = imgA.getHeight
      // Loop over every pixel.
      for (y <- 0 until height) {
        for (x <- 0 until width) {
          // Compare the pixels for equality.
          if (imgA.getRGB(x, y) != imgB.getRGB(x, y)) return false
        }
      }
      true
    }
  }

  def matchSnapshot[T]()(implicit s: SnapshotSerializer[T], test: TestData): Matcher[T] =
    new SnapshotShouldMatch[T](None)
  def matchSnapshot[T](explicitId: String)(implicit s: SnapshotSerializer[T], test: TestData): Matcher[T] =
    new SnapshotShouldMatch[T](Option(explicitId))
}
