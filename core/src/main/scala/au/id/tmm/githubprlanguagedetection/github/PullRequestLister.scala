package au.id.tmm.githubprlanguagedetection.github

import au.id.tmm.githubprlanguagedetection.github.configuration.{GitHubConfiguration, GitHubCredentials, GitHubInstance}
import au.id.tmm.githubprlanguagedetection.github.model.{Commit, PullRequest, RepositoryName}
import au.id.tmm.utilities.codec.binarycodecs._
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import org.kohsuke.github.{
  GHCommitPointer,
  GHIssueState,
  GHPullRequest,
  GitHub => GitHubClient,
  GitHubBuilder => GitHubClientBuilder,
}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class PullRequestLister(
  gitHubConfiguration: GitHubConfiguration,
) {

  def listPullRequestsFor(repository: RepositoryName): IO[ArraySeq[PullRequest]] =
    for {
      client             <- IO(makeClient)
      repository         <- IO(client.getRepository(s"${repository.owner}/${repository.repo}"))
      apiPullRequests    <- IO(repository.getPullRequests(GHIssueState.ALL).asScala.to(ArraySeq))
      parsedPullRequests <- IO.fromEither(apiPullRequests.traverse(parsePullRequest))
    } yield parsedPullRequests

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
      number <- requireNonNull(apiPR.getNumber)
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
    requireNonNull(apiCommit.getSha).flatMap(_.parseHex).map(Commit.apply)

  private def requireNonNull[A](a: A): ExceptionOr[A] =
    if (a == null) Left(GenericException("Encountered null")) else Right(a)

}
