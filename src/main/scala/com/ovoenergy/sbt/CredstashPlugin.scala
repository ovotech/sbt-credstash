import sbt._
import sbt.Keys._

import scala.util.matching.Regex.Match

object CredstashPlugin extends AutoPlugin {
  // by defining autoImport, the settings are automatically imported into user's `*.sbt`
  object autoImport {
    val credstashPopulateConfig = taskKey[Seq[File]]("Makes copies of config files with all placeholders substituted with the corresponding secret downloaded from credstash.")
    val credstashInputDir = settingKey[File]("This directory will be recursively searched for files to process.")
    val credstashOutputDir = settingKey[File]("The processed files will be written to this directory. This should be somewhere you are not likely to accidentally check in to git, e.g. under the `target` directory.")
    val credstashFileFilter = settingKey[String]("Only files matching this filter will be processed. e.g. `*.conf`")

    lazy val baseCredstashSettings: Seq[Def.Setting[_]] = Seq(
      credstashInputDir := (resourceDirectory in Compile).value,
      credstashFileFilter := "*.*",
      credstashOutputDir := target.value / "credstash",
      credstashPopulateConfig := {
        val oldBase = credstashInputDir.value
        val newBase = credstashOutputDir.value
        val fileFilter = credstashFileFilter.value
        Credstash(oldBase, newBase, fileFilter)
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
  val regex = """@@\{([^\}]+)\}""".r

  def downloadFromCredstash(keyMatcher: Match): String = {
    val key = keyMatcher.group(1)
    try {
      s"credstash get $key".!!.trim()
    } catch {
      case e: Throwable => throw new Exception(s"Failed to get value for $key from credstash")
    }
  }

  def apply(oldBase: File, newBase: File, fileFilter: String): Seq[File] = {
    val configFiles = (oldBase ** fileFilter).get
    val rebaser = rebase(oldBase, newBase)

    configFiles.map { file =>
      val fileContent = IO.read(file)

      val populatedConfig: String = regex.replaceAllIn(fileContent, m => downloadFromCredstash(m))
      val newFile = rebaser(file).get
      IO.write(newFile, populatedConfig)
      newFile
    }
  }
}
