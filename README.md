# sbt-credstash

[ ![Download](https://api.bintray.com/packages/ovotech/sbt-plugins/sbt-credstash/images/download.svg) ](https://bintray.com/ovotech/sbt-plugins/sbt-credstash/_latestVersion)

An sbt plugin to help you manage secrets using [credstash](https://github.com/fugue/credstash).

## Tasks

* `credstashPopulateConfig` - Reads your config files and makes copies of them, with placeholders replaced by their corresponding credstash-managed secret.

## Settings

* `credstashInputDir` - This directory will be recursively searched for files to process. Defaults to the resources directory (`src/main/resources`)
* `credstashFileFilter` - Only files matching this pattern will be processed. Defaults to `*.*`
* `credstashOutputDir` - Processed files will be written to this directory. Defaults to `target/credstash`.

## Example usage

1. Install credstash (`pip install credstash`) and configure it. See the [credstash README](https://github.com/fugue/credstash) for details.

2. Add a secret to credstash: `$ credstash put prod.db.password pa55word1`

3. Reference the secret using a placeholder in a config file:

   ```
   # src/main/resources/prod.conf
   db.password = @@{prod.db.password}
   ```

4. Add the plugin to your project. See the badge above for the latest version.

   ```
   // project/plugins.sbt
   resolvers += Resolver.bintrayIvyRepo("ovotech", "sbt-plugins")
   addSbtPlugin("com.ovoenergy" % "sbt-credstash" % "<version>")
   ```

5. Execute the `credstashPopulateConfig` task: `$ sbt credstashPopulateConfig`

6. You should end up with a copy of your config file, with all secrets included, in the `target/credstash` directory:

   ```
   # target/credstash/prod.conf
   db.password = pa55word1
   ```
