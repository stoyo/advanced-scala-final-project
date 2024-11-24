package fmi.scala.tagging.provider

import cats.effect.IO

class TaggingProviderService(taggingProviderRepository: TaggingProviderRepository) {

  def getAll: IO[List[TaggingProviderEntity]] = taggingProviderRepository.getAll

  def getById(id: Int): IO[Option[TaggingProviderEntity]] =
    taggingProviderRepository.retrieveById(id)

  def getByTitle(title: String): IO[Option[TaggingProviderEntity]] =
    taggingProviderRepository.retrieveByTitle(title)
}
