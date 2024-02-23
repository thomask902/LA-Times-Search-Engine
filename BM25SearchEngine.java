// Program performs bm25 search and retrieval from document collection directory created by IndexEngine program.

// TO RUN:
// javac BM25SearchEngine.java
// java BM25SearchEngine "/Users/thomaskleinknecht/Desktop/MSCI 541/latimes-index" 

// TO RUN (TEST FILES):
// javac BM25SearchEngine.java
// java BM25SearchEngine "/Users/thomaskleinknecht/Desktop/MSCI 541/HWTEST/latimes-index"

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class BM25SearchEngine {

    public static void main(String[] args) {

        //checking for command line arguments
        if (args.length != 1) {
            System.out.println("Please provide a path to your latimes-index directory.");
        } else {
            // location of saved file information and mappings
            String indexPath = args[0];
            File index = new File(indexPath);

            if (!index.exists()) {
                System.out.println("Please provide the proper path to the latimes-index file. This directory does not exist.");
            } else {

                // building lexicon and inverted index for tokenization of the query and searching
                HashMap<String, Integer> lexicon = buildLexicon(indexPath);
                HashMap<Integer, ArrayList<Integer>> invertedIndex = buildInvertedIndex(indexPath);

                // building arraylist of docnos for exporting docno in results
                ArrayList<String> docnos = buildDocnos(indexPath);

                // building arraylist of doc lengths for calculating scores
                ArrayList<Integer> docLengths = buildDocLengths(indexPath);

                // Calculating average doc length
                int sum = 0;
                for(int length : docLengths) {
                    sum += length;
                }
                double avgLength = (double) sum / docLengths.size();

                // total number of docs in collection
                int numDocs = docnos.size();

                // now ready to perform retrieval and take in queries
                
                // get search topic and query, tokenize query
                Scanner scanner = new Scanner(System.in);

                performSearch(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths);

                // after all is complete close scanner
                scanner.close();
            }
        }
    }
    
    // method to read in lexicon from file saved at indexPath
    public static HashMap<String, Integer> buildLexicon(String indexPath) {
        HashMap<String, Integer> lexicon = new HashMap<>();

        try {
            File terms = new File(indexPath + "/lexicon.txt");
            Scanner scanner = new Scanner(terms);

            int id = 0;
            while (scanner.hasNextLine()) {
                String term = scanner.nextLine();
                lexicon.put(term, id);
                id++;
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return lexicon;
    }

    // method to read in inverted index from file saved at indexPath
    public static HashMap<Integer, ArrayList<Integer>> buildInvertedIndex(String indexPath) {
        HashMap<Integer, ArrayList<Integer>> invertedIndex = new HashMap<>();

        try {
            File indexData = new File(indexPath + "/inverted-index.txt");
            Scanner scanner = new Scanner(indexData);

            while (scanner.hasNextLine()) {
                // parse term id and save as int
                String line1 = scanner.nextLine();
                int termID = Integer.parseInt(line1);
                
                // parse doc id and count from posting list and add to arraylist
                String line2 = scanner.nextLine();
                String[] values = line2.split("\\s");
                ArrayList<Integer> postings = new ArrayList<>();
                for(String s : values) {
                    postings.add(Integer.parseInt(s));
                }
                
                // add term id and posting list to index
                invertedIndex.put(termID, postings);
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return invertedIndex;
    }

    // reads in arraylist of docnos corresponding to doc id at that index from stored mapping in files
    public static ArrayList<String> buildDocnos(String indexPath) {
        ArrayList<String> docnos = new ArrayList<>();
        try {
            File inputFile = new File(indexPath + "/DOCNOs.txt");
            Scanner input = new Scanner(inputFile);

            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                docnos.add(line);
            }
            input.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        
        return docnos;
    }

    // reads in arraylist of doc lengths corresponding to doc id at that index from stored mapping in files
    public static ArrayList<Integer> buildDocLengths(String indexPath) {
        ArrayList<Integer> docLengths = new ArrayList<>();
        try {
            File inputFile = new File(indexPath + "/doc-lengths.txt");
            Scanner input = new Scanner(inputFile);

            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                int length = Integer.parseInt(line);
                docLengths.add(length);
            }
            input.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        
        return docLengths;
    }

    // tokenizer method, input String search query, arraylist of string tokens output
    public static ArrayList<String> tokenizer (String allText) {
        ArrayList<String> tokens = new ArrayList<>();

        // first text to lowercase
        allText = allText.toLowerCase();
        int start = 0;
        int i = 0;

        // for each character, checking if that character is non-alphanumeric, if so, return string preceding it if it exists
        for (i = 0; i < allText.length(); i++) {
            char c = allText.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (start != i) {
                    String token = allText.substring(start, i);
                    tokens.add(token);
                }

                //update start for start of next token
                start = i + 1;
            }
        }

        // last digit(s) are alphanumeric so we need last token
        if (start != i) {
            tokens.add(allText.substring(start, i));
        }

        return tokens;
    }

    // tokens to ids method, reads in token list of strings and lexicon and returns list of integers which it gets from lexicon
    public static ArrayList<Integer> convertTokensToIDs (ArrayList<String> tokens, HashMap<String, Integer> lexicon) {
        ArrayList<Integer> tokenIDs = new ArrayList<>();
        for (String token : tokens) {
            if(lexicon.containsKey(token)) {
                tokenIDs.add(lexicon.get(token));
            }
        }
        return tokenIDs;
    }

    // runs BM25 retrieval with inputted query and returns accumulator map with unordered scores for each relevant doc
    public static HashMap<Integer, Double> bm25Retrieval (ArrayList<Integer> tokenIDs, HashMap<Integer, ArrayList<Integer>> invertedIndex, int numDocs, double avgLength, ArrayList<Integer> docLengths ) {
        
        // creating accumulator for this query to store scores
        HashMap<Integer, Double> accum = new HashMap<>();

        // iterate through each query term at a time
        for(int i = 0; i < tokenIDs.size(); i++) {
            ArrayList<Integer> posting = invertedIndex.get(tokenIDs.get(i));
            int termDocs = posting.size() / 2;

            double insideLog = ((double) numDocs - termDocs + 0.5) / (termDocs + 0.5);
            double idf = Math.log(insideLog);

            for (int j = 0; j < posting.size() - 1; j += 2) {
                int docID = posting.get(j);
                int freq = posting.get(j + 1);
                int docLength = docLengths.get(docID);

                double lengthRatio = (double) docLength / avgLength;
                double k = 1.2 * (0.25 + 0.75 * lengthRatio);
                double tf = (double) freq / (k + freq);
                double score = tf * idf;

                if(accum.containsKey(docID)) {
                    double prevScore = accum.get(docID);
                    double newScore = prevScore + score;
                    accum.put(docID, newScore);
                } else {
                    accum.put(docID, score);
                }
            }
        }
        return accum;
    }

    // takes path and docno and returns string array of date and headline
    public static String[] getMetadata(String docno, String indexPath) {
        // take DOCNO, extract date
        String MM = docno.substring(2, 4);
        String DD = docno.substring(4, 6);
        String YY = docno.substring(6, 8);

        // use the date to get metadata and read in each line to its respective variable
        File metadata = new File(indexPath + "/" + YY + "/" + MM + "/" + DD + "/METADATA/" + docno + ".txt");
        String date = "";
        String headline = "";
        if (!metadata.canRead()) {
            System.out.println("Cannot read metadata file.");
        } else {
            try {
                Scanner inputMeta = new Scanner(metadata);
                String readDocno = inputMeta.nextLine().trim();
                String readInternalID = inputMeta.nextLine().trim();
                readDocno = readDocno + readInternalID;
                date = inputMeta.nextLine().trim();
                if(inputMeta.hasNextLine()) {
                    headline = inputMeta.nextLine().trim();
                } else {
                    headline = ""; 
                }
                
                inputMeta.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        String[] metadataArray = {date, headline};
        return metadataArray;
    }

    // takes docno and path and returns raw document
    public static String getRawDoc(String indexPath, String docno) {
        // take DOCNO, extract date
        String MM = docno.substring(2, 4);
        String DD = docno.substring(4, 6);
        String YY = docno.substring(6, 8);

        // use YY, MM, DD and DOCNO and return raw document
        File doc = new File(indexPath + "/" + YY + "/" + MM + "/" + DD + "/DOCUMENT/" + docno + ".txt");
        String rawDoc = "";
        if (!doc.canRead()) {
            System.out.println("Cannot read raw document file.");
        } else {
            try {
                Scanner inputDoc = new Scanner(doc);
                rawDoc += inputDoc.nextLine();
                while (inputDoc.hasNextLine()) {
                    String line = inputDoc.nextLine();
                    rawDoc += "\n" + line;
                }
                inputDoc.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return rawDoc;
    }

    // removes all XML tags from a doc, but does not remove spaces or empty lines
    public static String removeTags(String rawDoc) {
        int index = 0;
        String doc = "";
        if (rawDoc.indexOf("<") >= 0) {
            // takes substrings between > and < characters to remove XML tags
            while (index < rawDoc.length()) {
                doc += rawDoc.substring(index, rawDoc.indexOf("<", index));
                index = rawDoc.indexOf(">", index) + 1;
            }
        }
        return doc;
    }

    // takes doc as input and splits into sentences, yet to be fleshed out
    public static ArrayList<String> toSentences(String rawDoc, boolean noHeadline) {
        String outputDoc = "";

        if(noHeadline) {
            // remove tags
            outputDoc = removeTags(rawDoc);

            //remove newlines
            outputDoc = outputDoc.replaceAll("\\s+", " ");
            outputDoc = outputDoc.trim();

            // remove first 50 characters as they are in headline now
            outputDoc = outputDoc.substring(50);
        } else {
            // removing docno, date, length, and headline (if headline exists)
            if(rawDoc.indexOf("</HEADLINE>") != -1) {
                rawDoc = rawDoc.substring(rawDoc.indexOf("</HEADLINE>") + 11);

            } else if(rawDoc.indexOf("</LENGTH>") != -1) {
                rawDoc = rawDoc.substring(rawDoc.indexOf("</LENGTH>") + 9);
            }

            // remove tags from doc
            outputDoc = removeTags(rawDoc);

            //remove newlines
            outputDoc = outputDoc.replaceAll("\\s+", " ");
        }

        outputDoc = outputDoc.replaceAll("[.!?]", "$0|||");
        String[] sentences = outputDoc.split("\\|\\|\\|");
        ArrayList<String> sentenceList = new ArrayList<>();

        // iterate through each sentence string and tokenize into arraylist of word strings
        for(String s : sentences) {
            s = s.trim();
            if(!s.equals("")) {
                sentenceList.add(s);
            }
        }
        return sentenceList;
    } 

    // takes doc and query and returns query based snippet
    public static String snippetEngine(String rawDoc, ArrayList<Integer> queryIDs, HashMap<String, Integer> lexicon, boolean noHeadline) {

        // take doc and split into sentences
        ArrayList<String> sentences = toSentences(rawDoc, noHeadline);

        // take these sentences and tokenize, then use lexicon to go from token to id
        ArrayList<ArrayList<Integer>> sentenceTokenID = new ArrayList<>();
        for (String s : sentences) {
            ArrayList<String> sentenceTokens = tokenizer(s);
            ArrayList<Integer> sentenceIDs = convertTokensToIDs(sentenceTokens, lexicon);
            sentenceTokenID.add(sentenceIDs);
        }

        // score sentences based on query
        ArrayList<Integer> sentenceScores = new ArrayList<>();
        for(int i = 0; i < sentenceTokenID.size(); i++) {
            ArrayList<Integer> sentenceID = sentenceTokenID.get(i);
            
            // score for first or second sentence
            int l = 0;
            if(i == 0) {
                l = 2;
            } else if (i == 1) {
                l = 1;
            }

            // score for total count of query term occurences in sentence
            int c = 0;
            // score for longest continuous string of query terms
            int s = 0;

            for(int j = 0; j < sentenceID.size(); j++) {
                int sToken = sentenceID.get(j);
                if(queryIDs.contains(sToken)) {
                    c += 1;

                    if(s == 0) {
                        s = 1;
                    }

                    // is next term a query term
                    int m = j + 1;
                    int n = 1;
                    while(m < sentenceID.size()) {
                        if(!queryIDs.contains(sentenceID.get(m))) {
                            break;
                        }
                        n += 1;
                        if (n > s) {
                            s = n;
                        }
                        m += 1;
                    }
                }
            }

            // score for distinct query terms in sentence
            int d = 0;
            for(int q : queryIDs) {
                if(sentenceID.contains(q)) {
                    d += 1;
                }
            }

            // sum all scores and store in sentenceScores
            sentenceScores.add(l + c + s + d);

            // testing
            //System.out.println("Sentence " + i + ": l = " + l + ", c = " + c + ", s = " + s + ", d = " + d + ", Total Score = " + l + c + s + d);
        }

        // if just one sentence, return it
        if(sentences.size() == 1) {
            //System.out.println("Only one sentence, returning it");
            return sentences.get(0);
        } else {
            int highest = 0;
            int second = 1;

            for(int y = 0; y < sentenceScores.size(); y++) {
                if (sentenceScores.get(y) > sentenceScores.get(highest)) {
                    second = highest;
                    highest = y;
                } else if(sentenceScores.get(y) > sentenceScores.get(second)) {
                    if(highest != y) {
                        second = y;
                    }
                }
            }
            if(highest == second) {
                //System.out.println("Highest = Second, sentence: " + highest);
                return sentences.get(highest);
            } else {
                //System.out.println("Two sentences returned: " + highest + " and " + second);
                return sentences.get(highest) + " ... " + sentences.get(second);
            }
            
        }
    }

    public static void performSearch(Scanner scanner, String indexPath, int numDocs, double avgLength, HashMap<String, Integer> lexicon, HashMap<Integer, ArrayList<Integer>> invertedIndex, ArrayList<String> docnos, ArrayList<Integer> docLengths) {
        System.out.println("Please enter a query. When you have finished typing, hit enter to search! :)");
        String query = scanner.nextLine();
        System.out.println();
        
        long start = System.currentTimeMillis();
        ArrayList<String> tokens = tokenizer(query);
        ArrayList<Integer> tokenIDs = convertTokensToIDs(tokens, lexicon);

        // run BM25 retrieval and returned un-ordered map of all relevant docs with scores
        HashMap<Integer, Double> accum = bm25Retrieval(tokenIDs, invertedIndex, numDocs, avgLength, docLengths);

        // creating a map ordered by score descending for output (https://howtodoinjava.com/java/sort/java-sort-map-by-values/)
        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(accum.entrySet());

        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        ArrayList<String> top10Docno = new ArrayList<>();

        // iterating through top 10 results and ouputting to result file 
        int rank = 1;
        for (Map.Entry<Integer, Double> result : sorted) {
            int docID = result.getKey();
            String docno = docnos.get(docID);
            top10Docno.add(docno);

            // get raw document
            String rawDoc = getRawDoc(indexPath, docno);

            // removing tags from rawDoc for output purposes (not necessary)
            // String outputDoc = removeTags(rawDoc);

            // get metadata
            String[] metadata = getMetadata(docno, indexPath);
            String date = metadata[0];
            String headline = metadata[1];
            String snippet = "";

            // if headline is empty string make it first 50 char of doc from text or graphic
            if(headline.equals("")) {
                // remove first sections if they exist
                if(rawDoc.indexOf("</LENGTH>") != -1) {
                    rawDoc = rawDoc.substring(rawDoc.indexOf("</LENGTH>") + 9);
                } else if(rawDoc.indexOf("</SECTION>") != -1) {
                    rawDoc = rawDoc.substring(rawDoc.indexOf("</SECTION>") + 10);
                } else if(rawDoc.indexOf("</DATE>") != -1) {
                    rawDoc = rawDoc.substring(rawDoc.indexOf("</DATE>") + 7);
                } else if(rawDoc.indexOf("</DOCID>") != -1) {
                    rawDoc = rawDoc.substring(rawDoc.indexOf("</DOCID>") + 9);
                }

                // remove tags
                String outputDoc = removeTags(rawDoc);

                //remove newlines
                outputDoc = outputDoc.replaceAll("\\s+", " ");
                outputDoc = outputDoc.trim();

                // output first 50 characters, these won't be included in snippet engine
                headline = outputDoc.substring(0, 50);
                snippet = snippetEngine(rawDoc, tokenIDs, lexicon, true);
                
            } else {
                snippet = snippetEngine(rawDoc, tokenIDs, lexicon, false);
            }


            // output doc details to console
            System.out.println(rank + ". " + headline + " (" + date + ")");
            System.out.println(snippet + " (" + docno + ")");
            System.out.println();
            
            rank++;
            if(rank == 11) {
                break;
            }
        }
        long end = System.currentTimeMillis();
        double time = (double) (end - start) / 1000;
        System.out.println("Retrieval took " + time + " seconds.");

        // take in command of what to do next
        System.out.println("If you would like to see any of the documents, enter its result number. You can also type N for a new query or Q for quit.");
        takeCommand(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths, top10Docno);
    }

    public static void takeCommand(Scanner scanner, String indexPath, int numDocs, double avgLength, HashMap<String, Integer> lexicon, HashMap<Integer, ArrayList<Integer>> invertedIndex, ArrayList<String> docnos, ArrayList<Integer> docLengths, ArrayList<String> top10Docno) {
        String command = scanner.nextLine();

        if(command.matches("-?\\d+(\\.\\d+)?")) {
            int index = Integer.parseInt(command);
            if(index > 0 && index < 11) {
                String docno = top10Docno.get(index - 1);
                String roughDoc = getRawDoc(indexPath, docno);
                String cleanDoc = removeTags(roughDoc);
                System.out.println("Here is the full document:");
                System.out.println(cleanDoc);
                System.out.println("If you would like to see another one of the documents, enter its result number. You can also type N for a new query or Q for quit.");
                takeCommand(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths, top10Docno);
            } else {
                System.out.println("Please enter a valid document number! You can also type N for a new query or Q for quit.");
                takeCommand(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths, top10Docno);
            }
        } else if(command.equals("N")) {
            performSearch(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths);
        } else if(!command.equals("Q")) {
            System.out.println("Please enter a valid command!");
            takeCommand(scanner, indexPath, numDocs, avgLength, lexicon, invertedIndex, docnos, docLengths, top10Docno);
        }
    }
}