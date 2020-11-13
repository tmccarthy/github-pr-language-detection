package au.id.tmm.githubprlanguagedetection.cli

import java.time.ZoneId

import au.id.tmm.collections.DupelessSeq
import au.id.tmm.githubprlanguagedetection.cli.CliConfig.ReportConfig
import au.id.tmm.githubprlanguagedetection.linguist.model.Language
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport
import au.id.tmm.plotly.Plot
import au.id.tmm.plotly.model._
import au.id.tmm.plotly.syntax._

import scala.collection.immutable.ArraySeq

object ReportPlots {

  def plotTemporalReport(
    report: GitHubPrLanguageDetectionReport,
    config: ReportConfig,
  ): Plot = {

    val languagesToBreakOut: DupelessSeq[Language] = report.proportionalReport.countsPerLanguage
      .to(ArraySeq)
      .sortBy { case (language, count) => count }
      .map { case (language, count) => language }
      .take(config.numLanguagesToBreakOut.getOrElse(8))
      .to(DupelessSeq)

    val languagesForPlot: DupelessSeq[LanguageForPlot] =
      languagesToBreakOut.map(LanguageForPlot.Of.apply) :+
        LanguageForPlot.Others

    implicit val orderLanguagesForPlot: Ordering[LanguageForPlot] = {
      val languageIndexes = languagesToBreakOut.zipWithIndex.toMap

      Ordering
        .by[LanguageForPlot, Int] {
          case LanguageForPlot.Of(lang) => languageIndexes.getOrElse(lang, Int.MinValue)
          case LanguageForPlot.Others   => Int.MinValue
        }
        .reverse
    }

    val temporalReport = report.temporalReport(
      config.temporalReportStartDate,
      config.temporalReportBinSize,
      config.timeZone.getOrElse(ZoneId.systemDefault()),
    )

    val (dates, countsPerDate) = temporalReport.countsPerPeriod.map {
      case (date, countsPerLanguage) =>
        date ->
          countsPerLanguage
            .groupMap[LanguageForPlot, Int] {
              case (language, count) if languagesToBreakOut.contains(language) => LanguageForPlot.Of(language)
              case (language, count)                                           => LanguageForPlot.Others
            } {
              case (language, count) => count
            }
            .map {
              case (languageForPlot, counts) => languageForPlot -> counts.sum
            }
    }.unzip

    val traces = languagesForPlot.sorted.map { language =>
      Trace(
        traceType = Trace.Type.Scatter,
        stackgroup = "one",
        x = dates,
        y = countsPerDate.map(_(language)),
      )
    }

    Plot(
      traces,
      layout = Layout(
        title = Layout.Title(
          text = s"Languages for pull requests over time",
        ),
      ),
    )
  }

  private sealed trait LanguageForPlot

  private object LanguageForPlot {

    final case class Of(language: Language) extends LanguageForPlot
    final case object Others                extends LanguageForPlot

  }

}
