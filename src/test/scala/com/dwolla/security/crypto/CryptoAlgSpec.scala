package com.dwolla.security.crypto

import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.util.Date

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all._
import com.dwolla.testutils._
import io.chrisdavenport.log4cats.Logger
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck._
import shapeless.tag

import scala.concurrent.duration.MILLISECONDS

class CryptoAlgSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers with ScalaCheckPropertyChecks {
  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  private implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] =
    Arbitrary {
      Blocker[F]
        .flatMap(BouncyCastleResource[F](_, removeOnClose = false))
        .evalMap { _ =>
          for {
            pair <- Sync[F].delay {
              val generator = KeyPairGenerator.getInstance("RSA", "BC")
              generator.initialize(4096)
              generator.generateKeyPair()
            }
            now <- Clock[F].realTime(MILLISECONDS).map(new Date(_))
            pgpPair <- Sync[F].delay {
              new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
            }
          } yield pgpPair
        }
    }

  private implicit def arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    for {
      bytes <- arbitrary[Seq[Byte]]
    } yield Stream.emits(bytes)
  }

  private def arbPgpBytes[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, Array[Byte]]] = Arbitrary {
    for {
      keyPair <- arbKeyPair[F].arbitrary
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes
  }

  private implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    Gen.chooseNum(1, 4096).map(tag[ChunkSizeTag][Int])
  }

  it should "round trip the plaintext" in {
    forAll { (keyPairR: Resource[IO, PGPKeyPair],
              bytes: Stream[Pure, Byte],
              encryptionChunkSize: ChunkSize,
              decryptionChunkSize: ChunkSize) =>
      for {
        blocker <- Blocker[IO]
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
        keyPair <- keyPairR
        roundTrip <- bytes
          .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
          .through(crypto.decrypt(keyPair.getPrivateKey, decryptionChunkSize))
          .compile
          .resource
          .toList
      } yield {
        roundTrip should be(bytes.toList)
      }
    }
  }

  it should "support armoring a PGP value" in {
    forAll(arbPgpBytes[IO].arbitrary) { (bytesR: Resource[IO, Array[Byte]]) =>
      for {
        blocker <- Blocker[IO]
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
        bytes <- bytesR
        armored <- Stream.emits(bytes).through(crypto.armor()).through(text.utf8Decode).compile.resource.string
        expected <- Resource.liftF {
          for {
            out <- IO(new ByteArrayOutputStream())
            _ <- Resource.fromAutoCloseableBlocking(blocker)(IO(new ArmoredOutputStream(out))).evalMap(arm => IO(arm.write(bytes))).use(_ => IO.unit)
            s <- IO(new String(out.toByteArray))
          } yield s
        }
      } yield {
        armored should be(expected)
      }
    }
  }

  private implicit def ioCheckingAsserting[A]: CheckerAsserting[Resource[IO, A]] { type Result = IO[Unit] } =
    new ResourceCheckerAsserting[IO, A]

}
