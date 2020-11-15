# `github-pr-language-detection`

[![CircleCI](https://circleci.com/gh/tmccarthy/github-pr-language-detection/tree/master.svg?style=shield)](https://circleci.com/gh/tmccarthy/github-pr-language-detection/tree/master)
[![Maven Central](https://img.shields.io/maven-central/v/au.id.tmm.github-pr-language-detection/github-pr-language-detection-lib_2.13.svg)](https://repo.maven.apache.org/maven2/au/id/tmm/github-pr-language-detection/github-pr-language-detection-lib_2.13/)

A utility for detecting the programming languages used in pull requests against a GitHub repository.

Requires that `github-linguist` is installed and available on the `PATH` (see [instructions](https://github.com/github/linguist/#command-line-usage)).

## Usage

### Running on the command line

The project is configured using a json file of the following format:

```json
{
  "gitHubConfiguration": {
    "credentials": {
      "username": "email@example.com",
      "personalAccessToken": "<personal access token>"
    },
    "instance": "github.com"
  },
  "repositoryToScan": "tmccarthy/github-pr-language-detection",
  "performance": {
    "checkoutsPerMinute": 16,
    "maxConcurrent": 2,
    "checkoutTimeout": "PT10S",
    "languageCheckTimeout": "PT30S"
  },
  "reportConfig": {
    "output": "output.csv",
    "timeZone": "Australia/Melbourne",
    "languagesToIgnoreIfPossible": [
      "Shell"
    ]
  }
}
```

Provide the path to the config file to the `cli/run` SBT task:

```shell
./sbt "cli/run runconfig.json"
```

### Use as a library

Add the following to your `build.sbt` file:

```scala
libraryDependencies += "au.id.tmm.github-pr-language-detection" %% "github-pr-language-detection-lib" % "0.0.2"
```