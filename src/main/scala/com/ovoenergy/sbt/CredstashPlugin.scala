package com.ovoenergy.sbt.credstash
import sbt._
import sbt.Keys._
import sbt.complete._
import sbt.complete.DefaultParsers._

import scala.util.matching.Regex.Match
import scala.util.{Try,Success,Failure}

object CredstashPlugin extends AutoPlugin {
  // by defining autoImport, the settings are automatically imported into user's `*.sbt`
  object autoImport {
    val credstashPopulateConfig = taskKey[Seq[File]]("Makes copies of config files with all placeholders substituted with the corresponding secret downloaded from credstash.")
    val credstashCheckConfig = taskKey[Unit]("Checks that all placeholders in config files (in the `credstashInputDir`) refer to valid keys in credstash.")
    val credstashLookupSecret = inputKey[Option[String]]("Lookup a secret in credstash by key")

    val credstashInputDir = settingKey[File]("This directory will be recursively searched for files to process.")
    val credstashOutputDir = settingKey[File]("The files processed by the `credstashPopulateConfig` task will be written to this directory. This should be somewhere you are not likely to accidentally check in to git, e.g. under the `target` directory.")
    val credstashFileFilter = settingKey[String]("Only files matching this filter will be processed. e.g. `*.conf`")
    val credstashAwsRegion = settingKey[String]("AWS region containing the credstash DynamoDB table")
        
    private val singleArgumentParser = (Space ~ StringBasic) map { case (spaces, word) => word }

    lazy val baseCredstashSettings: Seq[Def.Setting[_]] = Seq(
      credstashInputDir := (resourceDirectory in Compile).value,
      credstashFileFilter := "*.*",
      credstashOutputDir := target.value / "credstash",
      credstashAwsRegion := "eu-west-1",
      credstashPopulateConfig := {
        val oldBase = credstashInputDir.value
        val newBase = credstashOutputDir.value
        val fileFilter = credstashFileFilter.value
        val region = credstashAwsRegion.value
        Credstash.populateConfig(oldBase, newBase, fileFilter, region, streams.value.log)
      },
      credstashCheckConfig := {
        val baseDir = credstashInputDir.value
        val fileFilter = credstashFileFilter.value
        val region = credstashAwsRegion.value
        Credstash.checkConfig(baseDir, fileFilter, region, streams.value.log)
      },
      credstashLookupSecret := {
        val credstashKey = singleArgumentParser.parsed
        val region = credstashAwsRegion.value
        Credstash.lookupSecret(credstashKey, region)
      }
    )
  }

  import autoImport._

  // This plugin is automatically enabled for all projects
  override def trigger = allRequirements

  // a group of settings that are automatically added to projects.
  override val projectSettings = baseCredstashSettings
}

object Credstash {
  // Replacing config value of format @@{foo.bar}
  private val regex = """@@\{([^\}]+)\}""".r

  private def downloadFromCredstash(keyMatcher: Match, region: String): String = {
    val key = keyMatcher.group(1)
    try {
      s"credstash -r $region get $key".!!.trim()
        .replaceAllLiterally("""\""", """\\""") // so backslashes don't get removed by `replaceAllIn`
    } catch {
      case e: Throwable => throw new Exception(s"Failed to get value for $key from credstash", e)
    }
  }

  def populateConfig(oldBase: File, newBase: File, fileFilter: String, region: String, log: Logger): Seq[File] = {
    log.info(s"Populating credstash placeholders in $oldBase/**/$fileFilter ...")

    val configFiles = (oldBase ** fileFilter).get
    val rebaser = rebase(oldBase, newBase)

    val outputFiles = configFiles.map { file =>
      log.info(s"Processing $file")
      val fileContent = IO.read(file)

      val populatedConfig: String = 
        regex.replaceAllIn(fileContent, m => downloadFromCredstash(m, region))
      val newFile = rebaser(file).get
      IO.write(newFile, populatedConfig)
      newFile
    }

    log.info("Finished populating credstash placeholders")
    outputFiles
  }

  def checkConfig(baseDir: File, fileFilter: String, region: String, log: Logger): Unit = {
    log.info(s"Checking credstash placeholders in $baseDir/**/$fileFilter ...")

    import scala.sys.process._
    val credstashKeys = {
      val lines = Process(s"credstash -r $region list").lines
      lines.map(_.takeWhile(_ != ' ')).toSet
    }

    val configFiles = (baseDir ** fileFilter).get
    val results: Iterable[Boolean] = configFiles.flatMap { file =>
      log.info(s"Processing $file")
      val fileContent = IO.read(file)
      regex.findAllMatchIn(fileContent) map { m =>
        val key = m.group(1)
        if (credstashKeys.contains(key))
          true
        else {
          log.warn(s"Key $key does not exist in credstash")
          false
        }
      }
    }

    if (results.toSet.contains(false))
      throw new Exception("At least one key missing from credstash")

    log.info("Finished checking credstash placeholders")
  }

  def lookupSecret(key: String, region: String): Option[String] = {
    Try(s"credstash -r $region get $key".!!.trim()).toOption
  }

}
