package org.allenai.ari.sentences

import org.allenai.ari.solvers.utils.Tokenizer

/**
 * Created by niranjan on 8/7/14.
 */
object QuestionKeywordsExtractor {

  def keywords(string: String) = Tokenizer.toKeywords(string)



  def main(args: Array[String]) = {

  }

}
