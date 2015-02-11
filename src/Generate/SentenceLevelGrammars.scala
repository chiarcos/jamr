package edu.cmu.lti.nlp.amr.Generate
import edu.cmu.lti.nlp.amr._

import scala.util.matching.Regex
import scala.collection.mutable.{Map, Set, ArrayBuffer}
import scala.io.Source
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream

object SentenceLevelGrammars {

    val usage = """Usage: scala -classpath . edu.cmu.lti.nlp.amr.Generate.SentenceLevelGrammars < amr_file > outfile"""
    type OptionMap = Map[Symbol, String]

    def parseOptions(map : OptionMap, list: List[String]) : OptionMap = {
        def isSwitch(s : String) = (s(0) == '-')
        list match {
            case Nil => map
            case "--weights" :: value :: l =>            parseOptions(map + ('weights -> value), l)
            case "--dev" :: l =>                         parseOptions(map + ('dev -> "true"), l)
            case "--rule-inventory" :: value :: l =>     parseOptions(map + ('ruleInventory -> value), l)
            case "--output" :: value :: l =>             parseOptions(map + ('output -> value), l)
            case "--dependencies" :: value :: tail =>    parseOptions(map + ('dependencies -> value), tail)
            case "--ner" :: value :: tail =>             parseOptions(map + ('ner -> value), tail)
            case "--snt" :: value :: tail =>             parseOptions(map ++ Map('notTokenized -> value), tail)
            case "--tok" :: value :: tail =>             parseOptions(map ++ Map('tokenized -> value), tail)
            case "-v" :: value :: tail =>                parseOptions(map ++ Map('verbosity -> value), tail)

            case option :: tail => println("Error: Unknown option "+option) 
                                   sys.exit(1)
        }
    }

    def main(args: Array[String]) {
        val options = parseOptions(Map(),args.toList)
        if (options.contains('help)) { println(usage); sys.exit(1) }

        if (options.contains('verbosity)) {
            verbosity = options('verbosity).toInt
        }

        //val input : Array[Input] = Input.loadInputfiles(options)

        val ruleInventory: RuleInventory = new RuleInventory()
        ruleInventory.load(options('ruleInventory))

        val ruleModel = new SyntheticRules.Decoder(ruleInventory)

        var i = 0
        for (block <- Corpus.getAMRBlocks(Source.stdin.getLines)) {
            logger(0,"**** Processsing Block *****")
            logger(0,block)
            val data = AMRTrainingData(block)
            val sentence = data.sentence
            //val pos =  projectPos(input(i).pos)
            val graph = data.toOracleGraph(clearUnalignedNodes = true)  // TODO: don't require aligned sentence
            // see http://stackoverflow.com/questions/10887828/string-to-gzipoutputstream
            var writer : BufferedWriter = null
            try {
                val gzFile = new GZIPOutputStream(new FileOutputStream(new File(options('output) + s"/grammar${i}.gz")))
                writer = new BufferedWriter(new OutputStreamWriter(gzFile, "UTF-8"))
                for (node <- graph.nodes) {
                    val corpusRules /*: List[(Rule, FeatureVector)]*/ = ruleInventory.getRules(node)
                    val passThroughRules /*: List[(Rule, FeatureVector)]*/ = ruleInventory.passThroughRules(node)
                    val syntheticRules : List[Rule] = ruleModel.syntheticRules(SyntheticRules.Input(node, graph))
                    val rules = corpusRules ::: syntheticRules  // TODO: features on the rules
                    for (rule <- corpusRules) {
                        writer.append(rule.mkRule(withArgLabel=false)+" ||| corpus=1\n")
                    }
                    for (rule <- passThroughRules) {
                        writer.append(rule.mkRule(withArgLabel=false)+" ||| passthrough=1\n")
                    }
                    for (rule <- syntheticRules) {
                        writer.append(rule.mkRule(withArgLabel=false)+" ||| synthetic=1\n")
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close
                }
            }
            //System.out.println("<seg grammar=\"" + options('output) + "/grammar" + i.toString + ".gz\"> " + Rule.graphToCFG(graph.root) + " </seg>")
            System.out.println("<seg id=\"" + i.toString + "\" grammar=\"" + options('output) + "/grammar" + i.toString + ".gz\"> " + Rule.graphToCFG(graph.root) + " ||| " + data.sentence.mkString(" ") + " </seg>")
            i += 1
        }
    }
}

