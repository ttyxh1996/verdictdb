package edu.umich.verdict.expr

import java.io.{PrintWriter, File}
import java.sql.ResultSet

import edu.umich.verdict.Configuration
import edu.umich.verdict.cli.ResultWriter
import edu.umich.verdict.connectors.DbConnector
import edu.umich.verdict.transformation.Parser

import scala.io.Source

class ErrorEstimationAccuracy() {
  var conf: Configuration = null
  var connector: DbConnector = null
  var nSamples = 1000
  var sampleSize = 0.01
  var table = "lineitem40"
  var nPoissonCols = 100
  var queries = Array(
    """
      |select
      |sum(quantity) as sum_qty,
      |sum(extendedprice) as sum_base_price,
      |avg(extendedprice) as avg_price,
      |count(*) as count_order
      |from
      |lineitem40
      |where
      |shipdate <= '1998-09-01'
      |and returnflag = 'A'
      |and linestatus = 'F'
    """.stripMargin)
  var exacts: Array[Array[Double]] = null
  var approximates: Array[Array[Array[ApproxResult]]] = null

  def execute(q: String): ResultSet = {
    Parser.parse(q).run(conf, connector)
  }

  def sampleName(i: Int) = s"error_test_${table}_s$i"

  def createSamples(): Unit = {
    for (i <- 1 to nSamples) {
      try {
        execute(s"create sample ${sampleName(i)} from $table with size $sampleSize% store $nPoissonCols poisson columns")
      } catch {
        case e: Exception => println(e.getMessage)
      }
    }
  }

  def removeSamples(): Unit = {
    for (i <- 1 to nSamples) {
      execute(s"drop sample ${sampleName(i)}")
    }
  }

  def runExacts() = {
    execute("set approximation = off")
    queries.zipWithIndex.foreach(q => {
      val pw = new PrintWriter(new File(s"error-test/${q._2}/exact"))
      ResultWriter.writeResultSet(pw, execute(q._1))
      pw.close()
    })
  }

  def runApproximates() = {
    execute("set approximation = on")
    queries.zipWithIndex.foreach(q => {
      val pw = new PrintWriter(new File(s"error-test/${q._2}/approx"))
      for (i <- 1 to nSamples) {
        execute(s"set bootstrap.fixed_sample = ${sampleName(i)}")
        ResultWriter.writeResultSet(pw, execute(q._1))
      }
      pw.close()
    })
  }

  def loadExacts() = {
    exacts = queries.indices.map(i => {
      Source.fromFile(s"error-test/$i/exact").getLines().toArray.apply(2).split("\\|").map(_.trim.toDouble)
    }).toArray
    exacts
  }

  def loadApproximates() = {
    if (exacts == null)
      loadExacts()
    approximates = queries.indices.map(q => {
      val nCols = exacts(q).length
      var lines = Source.fromFile(s"error-test/$q/approx").getLines().toArray
      lines = lines.indices.filter(_ % 3 == 2).map(lines).toArray
      (0 until nCols).map(i => {
        lines.map(line => {
          val vals = line.split("\\|").map(_.trim.toDouble)
          ApproxResult(vals(i), vals(nCols + 4 * i), vals(nCols + 4 * i + 1), vals(nCols + 4 * i + 2), vals(nCols + 4 * i + 3))
        })
      }).toArray
    }).toArray
    approximates
  }

  def printBias() = {
    queries.indices.foreach(q => {
      println(s"Bias for query $q:")
      approximates(q).indices.foreach(col => {
        val colVals = approximates(q)(col)
        val avg = colVals.map(_.value).sum / colVals.length
        val exact = exacts(q)(col)
        println(s"column $col: ${math.abs(avg - exact)} (${math.abs(100 * (avg - exact) / exact)}%)")
      })
    })
  }

  def printVarianceError() = {
    def variance(apps: Array[ApproxResult]) = {
      val avg = apps.map(_.value).sum / apps.length
      apps.map(num => (num.value - avg) * (num.value - avg)).sum / apps.length
    }
    queries.indices.foreach(q => {
      println(s"Variance error for query $q:")
      approximates(q).indices.foreach(col => {
        val colVals = approximates(q)(col)
        val exact = variance(colVals)
        val errorAvg = colVals.map(app => math.abs((app.variance - exact) / exact)).sum / colVals.length
        println(s"column $col: ${100 * errorAvg}%")
      })
    })
  }

  def connect() = {
    conf = new Configuration(new File(this.getClass.getClassLoader.getResource("expr/config.conf").getFile))
    connector = DbConnector.createConnector(conf)
  }

  def printConfidenceIntervalError(method: String = "diff") = {
    def exactConfidenceInterval(exactVal: Double, apps: Array[ApproxResult]) = {
      val confidence = .95
      val trials: Int = apps.length
      val sortedVals = apps.map(_.value).sortBy(x => math.abs(exactVal - x))
      val bound = sortedVals(math.ceil(trials * confidence - 1).asInstanceOf[Int])
      (exactVal - math.abs(exactVal - bound),
        exactVal + math.abs(exactVal - bound))
    }

    def confidenceIntervalError(exact: (Double, Double), approx: (Double, Double)) = {
      if (method == "diff")
        (math.abs(exact._1 - approx._1) + math.abs(exact._2 - approx._2)) / (exact._2 - exact._1)
      else
        math.abs((exact._2 - exact._1) - (approx._2 - approx._1)) / (exact._2 - exact._1)
    }

    queries.indices.foreach(q => {
      println(s"Confidence interval error for query $q:")
      approximates(q).indices.foreach(col => {
        val colVals = approximates(q)(col)
        val exact = exactConfidenceInterval(exacts(q)(col), colVals)
        val errorAvg = colVals.map(app => confidenceIntervalError(exact, (app.ciLow, app.ciHigh))).sum / colVals.length
        println(s"column $col: ${100 * errorAvg}%")
      })
    })
  }

  def main(args: Array[String]) {
    connect()

    createDirs()

    println("Running Exacts ...")
    runExacts()

    println("Creating Samples ...")
    createSamples()

    println("Creating Approximates ...")
    execute(s"set bootstrap.sample_size = $sampleSize%")
    runApproximates()

    loadExacts()
    loadApproximates()

    println("Bias:")
    printBias()

    println("Variance Error:")
    printVarianceError()

    println("Confidence Interval Error:")
    printConfidenceIntervalError()

    println("Removing Samples ...")
    removeSamples()
  }

  def createDirs(): Unit = {
    new File("error-test").mkdir()
    queries.indices.foreach(i => {
      new File(s"error-test/$i").mkdir()
      val pw = new PrintWriter(new File(s"error-test/$i/q"))
      pw.println(queries(i))
      pw.close()
    })
  }
}

case class ApproxResult(value: Double, ciLow: Double, ciHigh: Double, error: Double, variance: Double)

/*
Shell commands:

import edu.umich.verdict.expr._
var etest = new ErrorEstimationAccuracy()


 */