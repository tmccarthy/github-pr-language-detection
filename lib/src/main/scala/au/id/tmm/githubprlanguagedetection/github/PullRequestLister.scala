package au.id.tmm.githubprlanguagedetection.github

import au.id.tmm.githubprlanguagedetection.github.configuration.{GitHubConfiguration, GitHubCredentials, GitHubInstance}
import au.id.tmm.githubprlanguagedetection.github.model.{Commit, PullRequest, RepositoryName}
import au.id.tmm.digest4s.binarycodecs.syntax._
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister.LOGGER
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import mouse.string._
import org.kohsuke.github.{GHCommitPointer, GHIssueState, GHPullRequest, GitHub => GitHubClient, GitHubBuilder => GitHubClientBuilder}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class PullRequestLister(
  gitHubConfiguration: GitHubConfiguration,
) {

  def listPullRequestsFor(repositoryName: RepositoryName): IO[ArraySeq[PullRequest]] =
    for {
      client             <- IO(makeClient)
      repository         <- IO(client.getRepository(repositoryName.asString))
      apiPullRequests    <- IO(repository.getPullRequests(GHIssueState.ALL).asScala.to(ArraySeq))
      parsedPullRequests <- IO.fromEither(apiPullRequests.traverse(parsePullRequest))
      _ <- IO(LOGGER.info(s"Listed ${parsedPullRequests.size} PRs for ${repositoryName.asString}"))
    } yield parsedPullRequests.sortBy(_.number)

  private def makeClient: GitHubClient = {
    var clientBuilder = new GitHubClientBuilder()

    clientBuilder = gitHubConfiguration.instance match {
      case GitHubInstance.GitHubDotCom           => clientBuilder
      case GitHubInstance.GitHubEnterprise(base) => clientBuilder.withEndpoint(base.toString)
    }

    clientBuilder = gitHubConfiguration.credentials match {
      case GitHubCredentials.Anonymous                    => clientBuilder
      case GitHubCredentials.AccessToken(username, token) => clientBuilder.withPassword(username, token)
    }

    clientBuilder.build()
  }

  private def parsePullRequest(apiPR: GHPullRequest): ExceptionOr[PullRequest] =
    for {
      number      <- requireNonNull(apiPR.getNumber)
      whenCreated <- requireNonNull(apiPR.getCreatedAt).map(_.toInstant)
      whenClosed = Option(apiPR.getClosedAt).map(_.toInstant)
      title   <- requireNonNull(apiPR.getTitle)
      htmlUrl <- requireNonNull(apiPR.getHtmlUrl).map(_.toURI)
      isOpen  <- requireNonNull(apiPR.getState == GHIssueState.OPEN)
      patch   <- requireNonNull(apiPR.getPatchUrl).map(_.toURI)
      diff    <- requireNonNull(apiPR.getDiffUrl).map(_.toURI)
      issue   <- requireNonNull(apiPR.getIssueUrl).map(_.toURI)
      base    <- requireNonNull(apiPR.getBase).flatMap(parseCommit)
      head    <- requireNonNull(apiPR.getHead).flatMap(parseCommit)
      whenMerged = Option(apiPR.getMergedAt).map(_.toInstant)
    } yield PullRequest(
      number,
      whenCreated,
      whenClosed,
      title,
      htmlUrl,
      isOpen,
      patch,
      diff,
      issue,
      base,
      head,
      whenMerged,
    )

  private def parseCommit(apiCommit: GHCommitPointer): ExceptionOr[Commit] =
    for {
      sha <- requireNonNull(apiCommit.getSha).flatMap(_.parseHex)
      ref = Option(apiCommit.getRef)

      repository = Option(apiCommit.getRepository)

      repository <- repository.traverse { r =>
        for {
          repoName      <- requireNonNull(r.getName)
          repoOwnerName <- requireNonNull(r.getOwnerName)
          httpsCloneUri <- requireNonNull(r.getHttpTransportUrl).flatMap(_.parseURI)
          sshCloneUri   <- requireNonNull(r.getSshUrl)
        } yield Commit.Repository(
          Commit.Repository.CloneUris(sshCloneUri, httpsCloneUri),
          RepositoryName(repoOwnerName, repoName),
        )
      }
    } yield Commit(repository, ref, sha)

  private def requireNonNull[A](a: A): ExceptionOr[A] =
    if (a == null) Left(GenericException("Encountered null")) else Right(a)

}

object PullRequestLister {
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)
}