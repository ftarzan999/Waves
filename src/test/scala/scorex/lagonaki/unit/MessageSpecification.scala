package scorex.lagonaki.unit

import java.nio.ByteBuffer

import org.scalatest.FunSuite
import scorex.block.Block
import scorex.consensus.nxt.NxtLikeConsensusModule
import scorex.crypto.EllipticCurveImpl.SignatureLength
import scorex.lagonaki.server.LagonakiSettings
import scorex.network.message.{BasicMessagesRepo, Message, MessageHandler}
import scorex.transaction.{History, SimpleTransactionModule}

class MessageSpecification extends FunSuite {
  implicit lazy val settings = new LagonakiSettings("settings-test.json")
  implicit lazy val consensusModule = new NxtLikeConsensusModule
  implicit lazy val transactionModule = new SimpleTransactionModule

  private lazy val repo = new BasicMessagesRepo()
  private lazy val handler = new MessageHandler(repo.specs)

  test("ScoreMessage roundtrip 1") {
    val s1 = BigInt(Long.MaxValue) * 10000000

    val msg = Message(repo.ScoreMessageSpec, Right(s1), None)

    handler.parse(ByteBuffer.wrap(msg.bytes), None).get.data.get match {
      case scoreRestored: History.BlockchainScore =>
        assert(s1 == scoreRestored)

      case _ =>
        fail("wrong data type restored")
    }
  }

  test("GetSignaturesMessage roundtrip 1") {
    val e1 = 33: Byte
    val e2 = 34: Byte
    val s1: Block.BlockId = e2 +: Array.fill(SignatureLength - 1)(e1)

    val msg = Message(repo.GetSignaturesSpec, Right(Seq(s1)), None)
    handler.parse(ByteBuffer.wrap(msg.bytes), None).get.data.get match {
      case ss: Seq[Block.BlockId] =>
        assert(ss.head.sameElements(s1))
      case _ =>
        fail("wrong data type restored")
    }
  }

  test("SignaturesMessage roundtrip 1") {
    val e1 = 33: Byte
    val e2 = 34: Byte
    val s1 = e2 +: Array.fill(SignatureLength - 1)(e1)
    val s2 = e1 +: Array.fill(SignatureLength - 1)(e2)

    val msg = Message(repo.SignaturesSpec, Right(Seq(s1, s2)), None)
    handler.parse(ByteBuffer.wrap(msg.bytes), None).get.data.get match {
      case ss: Seq[Block.BlockId] =>
        assert(ss.head.sameElements(s1))
        assert(ss.tail.head.sameElements(s2))
      case _ =>
        fail("wrong data type restored")
    }
  }
}