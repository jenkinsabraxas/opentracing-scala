package com.github.fehu.opentracing.compile

import scala.reflect.internal.util.NoSourceFile
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.{ Plugin, PluginComponent }

import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.internal.samplers.ConstSampler
import io.opentracing.{ Tracer, Scope => TScope }

class ImplicitSearchTracingPlugin(val global: Global) extends Plugin {
  import ImplicitSearchTracingPlugin.tracer
  import global._

  val name: String = "TracingImplicitSearch"
  val description: String = "Traces implicit searches performed by scalac and reports them to local jaegertracing backend"
  val components: List[PluginComponent] = Nil

  analyzer.addAnalyzerPlugin(new ImplicitsTracingAnalyzer)

  class ImplicitsTracingAnalyzer extends analyzer.AnalyzerPlugin {
    override def pluginsNotifyImplicitSearch(search: global.analyzer.ImplicitSearch): Unit = {
      val pos = search.pos
      val line = if (pos.source != NoSourceFile) pos.lineContent else "<NoSourceFile>"
      tracer
        .buildSpan(showShort(search.pt))
        .withTag("type", search.pt.safeToString)
        .withTag("file", pos.source.path)
        .withTag("line", line)
        .withTag("pos",  pos.toString)
        .startActive(true)
      super.pluginsNotifyImplicitSearch(search)
    }

    override def pluginsNotifyImplicitSearchResult(result: global.analyzer.SearchResult): Unit = {
      closeScopeSafe(tracer.scopeManager.active())
      super.pluginsNotifyImplicitSearchResult(result)
    }

    private def showName(name0: String): String =
      name0.takeWhile(_ != '{').split('.').reverse match {
        case Array("Aux", name, _*) => name
        case Array(name, _*) => name
      }
    private def showShort(tpe: Type): String = showName(tpe.typeConstructor.toString)

    private def closeScopeSafe(scope: TScope): Unit =
      if (scope ne null) {
        try scope.close()
        catch { case _: IllegalStateException => }
      }
  }
}

object ImplicitSearchTracingPlugin {
  val tracerServiceName = "implicit search"

  implicit val tracer: Tracer = Configuration
    .fromEnv(tracerServiceName)
    .withSampler(
      SamplerConfiguration.fromEnv()
        .withType(ConstSampler.TYPE)
        .withParam(1)
    )
    .getTracer
}
