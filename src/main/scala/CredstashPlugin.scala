import sbt._
import sbt.Keys._

import scala.util.matching.Regex.Match

object CredstashPlugin extends AutoPlugin {
  // by defining autoImport, the settings are automatically imported into user's `*.sbt`
  object autoImport {
    val populateConfig = taskKey[Seq[File]]("Makes copies of config files with all placeholders substituted with the corresponding secret downloaded from credstash.")

    // default value for the task
    lazy val baseCredstashSettings: Seq[Def.Setting[_]] = Seq(
      populateConfig := {
        val oldBase = (resourceDirectory in Compile).value
        val newBase = target.value / "credstash"
        Credstash(oldBase, newBase)
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

  def apply(oldBase: File, newBase: File): Seq[File] = {
    val configFiles = (oldBase ** "*.conf").get
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