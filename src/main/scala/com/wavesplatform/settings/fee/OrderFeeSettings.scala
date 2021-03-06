package com.wavesplatform.settings.fee

import cats.data.Validated
import cats.implicits._
import com.wavesplatform.settings.fee.AssetType.AssetType
import com.wavesplatform.settings.fee.Mode.Mode
import com.wavesplatform.transaction.Asset
import com.wavesplatform.transaction.Asset.Waves
import com.wavesplatform.transaction.assets.exchange.AssetPair
import monix.eval.Coeval
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.ValueReader
import play.api.libs.json.{JsObject, Json}

import scala.util.Try

object OrderFeeSettings {

  type ErrorsListOr[A] = Validated[List[String], A]

  sealed trait OrderFeeSettings {

    /** Returns json for order fee settings taking into account fee that should be paid for matcher's account script invocation */
    def getJson(minMarcherFee: Long): Coeval[JsObject] = Coeval.evalOnce {
      Json.obj(
        this match {
          case FixedWavesSettings(baseFee) =>
            "fixedWaves" -> Json.obj(
              "baseFee" -> (baseFee + minMarcherFee)
            )
          case FixedSettings(defaultAssetId, minFee) =>
            "fixed" -> Json.obj(
              "assetId" -> defaultAssetId.maybeBase58Repr,
              "minFee"  -> minFee
            )
          case PercentSettings(assetType, minFee) =>
            "percent" -> Json.obj(
              "type"   -> assetType,
              "minFee" -> minFee
            )
        }
      )
    }
  }

  case class FixedWavesSettings(baseFee: Long)                     extends OrderFeeSettings
  case class FixedSettings(defaultAssetId: Asset, minFee: Long)    extends OrderFeeSettings
  case class PercentSettings(assetType: AssetType, minFee: Double) extends OrderFeeSettings

  implicit val orderFeeSettingsReader: ValueReader[OrderFeeSettings] = { (cfg, path) =>
    def getPrefixByMode(mode: Mode): String = s"$path.$mode"

    def validateSafe[T: ValueReader](settingName: String): ErrorsListOr[T] = {
      Validated.fromTry(Try(cfg.as[T](settingName))).leftMap(_ => List(s"Invalid setting $settingName value: ${cfg.getString(settingName)}"))
    }

    def validateByPredicate[T: ValueReader](settingName: String)(predicate: T => Boolean, errorMsg: String): ErrorsListOr[T] = {

      val settingValue   = cfg.as[T](settingName)
      val additionalInfo = Option(errorMsg).filter(_.nonEmpty).fold("")(m => s", $m")

      Validated.cond(predicate(settingValue), settingValue, List(s"Invalid setting $settingName value: $settingValue$additionalInfo"))
    }

    def validateAssetId(settingName: String)(assetIdStr: String): ErrorsListOr[Asset] = {
      Validated
        .fromTry(AssetPair.extractAssetId(assetIdStr))
        .leftMap(_ => List(s"Invalid setting $settingName value: $assetIdStr"))
        .ensure(List(s"Invalid setting $settingName value: $assetIdStr, asset must not be Waves"))(_ != Waves)
    }

    def validateFixedWavesSettings: ErrorsListOr[FixedWavesSettings] = {
      validateByPredicate[Long](s"${getPrefixByMode(Mode.FIXED_WAVES)}.base-fee")(_ > 0, "must be > 0") map FixedWavesSettings
    }

    def validateFixedSettings: ErrorsListOr[FixedSettings] = {

      val prefix                 = getPrefixByMode(Mode.FIXED)
      val assetSettingName       = s"$prefix.asset-id"
      val fixedMinFeeSettingName = s"$prefix.min-fee"

      val assetStr = cfg.getString(assetSettingName)

      (
        validateAssetId(assetSettingName)(assetStr),
        validateByPredicate[Long](fixedMinFeeSettingName)(_ > 0, "must be > 0")
      ) mapN FixedSettings
    }

    def validatePercentSettings: ErrorsListOr[PercentSettings] = {

      val prefix             = getPrefixByMode(Mode.PERCENT)
      val percentSettingName = s"$prefix.min-fee"

      (
        validateSafe[AssetType](s"$prefix.asset-type"),
        validateByPredicate[Double](percentSettingName)(percent => 0 < percent && percent <= 100, "required 0 < p <= 100")
      ) mapN PercentSettings
    }

    def getSettingsByMode(mode: Mode): ErrorsListOr[OrderFeeSettings] = mode match {
      case Mode.FIXED_WAVES => validateFixedWavesSettings
      case Mode.FIXED       => validateFixedSettings
      case Mode.PERCENT     => validatePercentSettings
    }

    validateSafe[Mode](s"$path.mode").toEither.flatMap(mode => getSettingsByMode(mode).toEither) match {
      case Left(errorsAcc)         => throw new Exception(errorsAcc.mkString("\n"))
      case Right(orderFeeSettings) => orderFeeSettings
    }
  }
}

object AssetType extends Enumeration {
  type AssetType = Value

  val AMOUNT    = Value("amount")
  val PRICE     = Value("price")
  val SPENDING  = Value("spending")
  val RECEIVING = Value("receiving")
}

object Mode extends Enumeration {
  type Mode = Value

  val FIXED_WAVES = Value("fixed-waves")
  val FIXED       = Value("fixed")
  val PERCENT     = Value("percent")
}
