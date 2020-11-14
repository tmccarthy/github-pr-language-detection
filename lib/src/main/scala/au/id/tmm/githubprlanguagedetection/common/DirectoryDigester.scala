package au.id.tmm.githubprlanguagedetection.common

import java.io.OutputStream
import java.nio.file.{Files, Path}
import java.security.{DigestOutputStream, MessageDigest}

import au.id.tmm.digest4s.digest.SHA256Digest
import cats.effect.{Bracket, Concurrent, IO}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.IteratorHasAsScala
import fs2._

class DirectoryDigester(numThreads: Int)(implicit concurrent: Concurrent[IO]) {

  private val NUM_FILES_PER_CHUNK = 20

  private def makeMessageDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

  /**
    * Computes a SHA-256 digest for every regular file in the directory. Then concatenates these digests according to the
    * ordering for `Path`, and returns a digest for that.
    * @param directory
    * @return
    */
  def digestFor(directory: Path): IO[SHA256Digest] =
    for {
      allFilesInOrder: ArraySeq[Path] <- Bracket[IO, Throwable].bracket(
        acquire = IO(Files.walk(directory)),
      )(
        use = stream => IO(stream.filter(p => Files.isRegularFile(p)).iterator.asScala.to(ArraySeq).sorted),
      )(
        release = stream => IO(stream.close()),
      )

      directoryDigest: SHA256Digest <-
        Stream
          .emits[IO, Path](allFilesInOrder)
          .chunkMin(NUM_FILES_PER_CHUNK)
          .parEvalMap(numThreads)(digestsFor)
          .compile
          .to(CollectorIntoDigest)
    } yield directoryDigest

  private def digestsFor(paths: Chunk[Path]): IO[Chunk[SHA256Digest]] =
    IO {
      val messageDigest: MessageDigest = makeMessageDigest()

      paths.map { path =>
        val digestOutputStream = new DigestOutputStream(OutputStream.nullOutputStream(), messageDigest)

        Files.copy(path, digestOutputStream)

        val digest = SHA256Digest(new ArraySeq.ofByte(messageDigest.digest()))

        messageDigest.reset()

        digest
      }
    }

  private object CollectorIntoDigest extends Collector[Chunk[SHA256Digest]] {
    override type Out = SHA256Digest

    override def newBuilder: Collector.Builder[Chunk[SHA256Digest], SHA256Digest] =
      new Collector.Builder[Chunk[SHA256Digest], SHA256Digest] {
        private val messageDigest: MessageDigest = makeMessageDigest()

        override def +=(c: Chunk[Chunk[SHA256Digest]]): Unit =
          c.foreach { c =>
            c.foreach { digest =>
              messageDigest.update(digest.asBytes.unsafeArray)
            }
          }

        override def result: SHA256Digest = SHA256Digest(new ArraySeq.ofByte(messageDigest.digest()))
      }

  }

}
