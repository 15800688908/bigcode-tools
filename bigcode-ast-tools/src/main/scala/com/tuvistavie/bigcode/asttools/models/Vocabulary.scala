package com.tuvistavie.bigcode.asttools.models


import org.apache.commons.text.StringEscapeUtils

import scalaz.syntax.std.boolean._

case class Vocabulary(
  items: Map[Int, VocabItem],
  strippedIdentifiers: Boolean
) {
  val size: Int = items.size

  lazy val vocabHash: Map[Token, Int] = items.map { case (index, item) => (item.token, index) }

  lazy val letters: Set[Token] = vocabHash.keySet

  def indexFor(token: Token): Int = {
    vocabHash.getOrElse(token, Vocabulary.unk)
  }

  val totalLettersCount: Int = items.map(_._2.count).sum

  def toTSV: String = {
    val baseHeaders = List("id", "type", "metaType", "count")
    val headers = strippedIdentifiers.option(baseHeaders).getOrElse(baseHeaders :+ "value").mkString("\t")

    val rows = items.toList.sortBy(_._1).map { case (index, item) =>
      val token = item.token
      val baseRow = List(index.toString, token.tokenType, token.metaType, item.count.toString)
      val row = strippedIdentifiers.option(baseRow).getOrElse(
        baseRow :+ StringEscapeUtils.escapeJson(token.value.getOrElse("")))
      row.mkString("\t")
    }

    (headers :: rows).mkString("\n")
  }
}

object Vocabulary {
  val unk: Int = -1

  def apply(items: Seq[VocabItem], strippedIdentifiers: Boolean = false): Vocabulary = {
    val mapItems = items.zipWithIndex.map(_.swap).toMap
    Vocabulary(mapItems, strippedIdentifiers)
  }

  def fromTSV(tsv: String): Vocabulary = {
    val linesIterator = tsv.lines
    val headers = linesIterator.next().split("\t")
    val strippedIdentifiers = headers.length == 4
    val vocabItems = linesIterator.foldLeft(Map.empty[Int, VocabItem]) { case (items, row) =>
      val item = row.split("\t")
      val hasValue = item.isDefinedAt(4) && item(4).length > 0
      val token = Token(item(1), hasValue.option(item(4)))
      items + (item(0).toInt -> VocabItem(token, item(3).toInt))
    }
    new Vocabulary(vocabItems, strippedIdentifiers)
  }
}