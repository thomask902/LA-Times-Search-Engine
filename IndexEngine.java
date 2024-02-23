// Program unzips and reads in files, parsing doc by doc and exporting meta data and raw data to directory organized by date of the article/doc

// TO RUN (locally):
// javac IndexEngine.java
// java IndexEngine "/Users/thomaskleinknecht/Desktop/MSCI 541/latimes.gz" "/Users/thomaskleinknecht/Desktop/MSCI 541/latimes-index"

// TO RUN (test files):
// javac IndexEngine.java
// java IndexEngine "/Users/thomaskleinknecht/Desktop/MSCI 541/HWTEST/testdocs.gz" "/Users/thomaskleinknecht/Desktop/MSCI 541/HWTEST/latimes-index"


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.HashMap;
import java.lang.Character;

public class IndexEngine {

    public static void main(String[] args) {
        
        
        //checking for command line arguments
        if (args.length != 2) {
            System.out.println("Please provide a path to the latimes.gz file and a path to a directory where the documents and metadata will be stored as arguments to the IndexEngine program. Ensure each path enclosed in quotations.");
        } else {
            // reading in zipped file directory
            File zippedFileDirectory = new File(args[0]);

            // location to put files after read
            File exportDirectory = new File(args[1]);

            // array to check to see if this directory is empty, if it isn't we will exit
            String[] files = exportDirectory.list();

            if (!zippedFileDirectory.exists()) {
                System.out.println("Please provide the proper path to the latimes.gz file. This directory does not exist.");
            } else if (!exportDirectory.exists()) {
                System.out.println("Please provide the proper path to the latimes-index file. This directory does not exist.");
            } else if (files.length != 0) {
                System.out.println("The directory of files already exists. Program has been stopped.");
            } else {
                // adding files for storing docnos, doc lengths, and lexicon
                createMapping(args[1]);
                
                // creating lexicon for mapping words to ids, and list to store the words in order of id for easy output
                HashMap<String, Integer> lexicon = new HashMap<>();
                ArrayList<String> lexiconWords = new ArrayList<>();

                // creating inverted index
                HashMap<Integer, ArrayList<Integer>> invertedIndex = new HashMap<>();
                
                // reading in file and executing program
                try {
                    FileInputStream fileInputStream = new FileInputStream(zippedFileDirectory);
                    GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                    InputStreamReader decoder = new InputStreamReader(gzipInputStream);
                    BufferedReader buffered = new BufferedReader(decoder);
                    String currentDoc = zippedToString(buffered);
                    int internalID = 0;

                    

                    // while loop checks to see that we are not at end of file, and if not it will perform each of our methods which we need to do to process each doc then go to the next
                    while (!currentDoc.equals("")) {
                        // extract DOCNO
                        String docNO = currentDoc.substring(currentDoc.indexOf("<DOCNO>") + 7, currentDoc.indexOf("</DOCNO>")).trim();

                        // extract headline, graphic, and text
                        String headline = extractSection(currentDoc, "HEADLINE");
                        String graphic = extractSection(currentDoc, "GRAPHIC");
                        String text = extractSection(currentDoc, "TEXT");
                        String allText = headline + " " + graphic + " " + text;

                        // tokenize allText
                        ArrayList<String> tokens = tokenizer(allText);

                        // read tokens to term ids and add to lexicon if needed
                        ArrayList<Integer> tokenIDs = convertTokensToIDs(tokens, lexicon, lexiconWords);

                        // find count of words in the doc
                        HashMap<Integer, Integer> wordCounts = countWords(tokenIDs);

                        // add word counts to inverted index with docID
                        addToPostings(wordCounts, internalID, invertedIndex);

                        // extract date from DOCNO
                        String MM = docNO.substring(2, 4);
                        String DD = docNO.substring(4, 6);
                        String YY = docNO.substring(6, 8);
                        String date = formatDate(DD, MM, YY);
                        
                        // export DOCNO and doc length to mapping files
                        mappingEntry(docNO, "DOCNOs.txt", args[1]);
                        String docLength = Integer.toString(tokens.size());
                        mappingEntry(docLength, "doc-lengths.txt", args[1]);
                        
                        // enter directory check for folders and create if needed
                        directorySetup(YY, MM, DD, args[1]);

                        // enter directory and export raw doc and metadata
                        String exactFilePath = args[1] + "/" + YY + "/" + MM + "/" + DD;
                        createFiles(currentDoc, docNO, internalID, date, headline, exactFilePath);

                        // increment internal ID
                        internalID += 1;
                        
                        // attempt to read in next doc
                        currentDoc = zippedToString(buffered);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                saveLexicon(lexiconWords, args[1]);
                saveInvertedIndex(invertedIndex, args[1]);
            }
        }
    }

    // method to read in zipped file and unzip it to access it as a string
    public static String zippedToString (BufferedReader buffered) throws IOException {
        //if (buffered == null) {
          //  return "";
        //}

        try {

        String line = buffered.readLine();
        String unzippedDoc = line;

        if (line == null || line.isEmpty()) {

          return "";

        } else {

            while(!line.equals("</DOC>")) {
                line = buffered.readLine();
                unzippedDoc = unzippedDoc + "\n" + line;
            }
    
            return unzippedDoc;    
        }

        } catch (IOException e) {
            e.printStackTrace();
            return "Error Occurred";
        }
    }

    // extract HEADLINE (remove any tags on inside) store string to memory as well ** SHOULD NOW BE DEFUNCT **
    public static String extractHeadline (String currentDoc) {
        if (currentDoc.indexOf("<HEADLINE>") < 0) {
            return "";
        } else {
            String headlineRaw = currentDoc.substring(currentDoc.indexOf("<HEADLINE>") + 10, currentDoc.indexOf("</HEADLINE>")).trim();
            int index = 0;
            String headline = "";
            if (headlineRaw.indexOf("<") >= 0) {
                // takes substrings between > and < characters to remove XML tags
                while (index < headlineRaw.length()) {
                    headline += headlineRaw.substring(index, headlineRaw.indexOf("<", index));
                    index = headlineRaw.indexOf(">", index) + 1;
                }
            } 
            // need to remove new lines and make them just spaces
            return headline.replaceAll("\\s+", " ").trim();
        }
    }

    // extract SECTION (remove any tags on inside) store string to memory as well (for headline, graphic and text)
    public static String extractSection (String currentDoc, String section) {
        if (currentDoc.indexOf("<" + section + ">") < 0) {
            return "";
        } else {
            String sectionRaw = currentDoc.substring(currentDoc.indexOf("<" + section + ">") + section.length() + 2, currentDoc.indexOf("</" + section +">")).trim();
            int index = 0;
            String result = "";
            if (sectionRaw.indexOf("<") >= 0) {
                // takes substrings between > and < characters to remove XML tags
                while (index < sectionRaw.length()) {
                    result += sectionRaw.substring(index, sectionRaw.indexOf("<", index));
                    index = sectionRaw.indexOf(">", index) + 1;
                }
            } 
            // need to remove new lines and make them just spaces
            return result.replaceAll("\\s+", " ").trim();
        }
    }

    // method to take DD, MM, and YY and store as formatted date
    public static String formatDate (String DD, String MM, String YY){
        int day = Integer.parseInt(DD);
        String[] months = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };
        String month = months[Integer.parseInt(MM) - 1];
        return month + " " + day + ", 19" + YY;
        
    }

    // create folder and file to house DOCNO mappings and doc length mappings
    public static void createMapping (String exportFilePath) {
        // File newFolder = new File(exportDirectory, "MAPPINGS");
        // newFolder.mkdir();
        // File exportMappingDirectory = new File(exportFilePath + "/MAPPINGS");

        File mappingDOCNO = new File(exportFilePath, "DOCNOs.txt");
        File mappingDocLength = new File(exportFilePath, "doc-lengths.txt");
        File mappingLexicon = new File(exportFilePath, "lexicon.txt");
        File mappingInvertedIndex = new File(exportFilePath, "inverted-index.txt");
        try {
            mappingDOCNO.createNewFile();
            mappingDocLength.createNewFile();
            mappingLexicon.createNewFile();
            mappingInvertedIndex.createNewFile();
        } catch (IOException e) {
            System.err.println("Error while creating DOCNO or doc length file: " + e.getMessage());
        }
    }

    // export DOCNO to DOCNO/Internal ID mapping file in directory, adding it to the next line, this line corresponds with internal ID
    public static void mappingEntry (String data, String fileName, String exportPath) {
        try {
            FileWriter writer = new FileWriter(exportPath + "/" + fileName, true);
            writer.write(data + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    // method to enter directory and check for to enter/add YY, MM, DD, then DOCUMENT and METADATA folders under DD as needed
    public static void directorySetup (String YY, String MM, String DD, String exportFilePath) {
        
        // checking/adding YY first
        File exportYear = new File(exportFilePath + "/" + YY);
        if (!exportYear.exists()) {
            File newYear = new File(exportFilePath, YY);
            newYear.mkdir();
        }

        //checking and adding MM
        File exportMonth = new File(exportFilePath + "/" + YY + "/" + MM);
        if (!exportMonth.exists()) {
            File newMonth = new File(exportFilePath + "/" + YY, MM);
            newMonth.mkdir();
        }

        //checking and adding DD
        File exportDay = new File(exportFilePath + "/" + YY + "/" + MM + "/" + DD);
        if (!exportDay.exists()) {
            File newDay = new File(exportFilePath + "/" + YY + "/" + MM, DD);
            newDay.mkdir();
            File rawDoc = new File(exportFilePath + "/" + YY + "/" + MM + "/" + DD, "DOCUMENT");
            rawDoc.mkdir();
            File metadata = new File(exportFilePath + "/" + YY + "/" + MM + "/" + DD, "METADATA");
            metadata.mkdir();
        }
    }

    // method to enter directory and export add raw doc stored to DOCNO.txt file  in DOCUMENT folder, and
    // append DOCNO, internal ID, date, and HEADLINE and export as DOCNO.txt to METADATA folder 
    public static void createFiles (String currentDoc, String docNO, int internalID, String date, String headline, String exactFilePath) {
        // creating document file and writing currentDoc to file
        File docFile = new File(exactFilePath + "/DOCUMENT", docNO + ".txt");
        try {
            docFile.createNewFile();
        } catch (IOException e) {
            System.err.println("Error while creating DOCUMENT file: " + e.getMessage());
        }
        try {
            FileWriter writer = new FileWriter(exactFilePath + "/DOCUMENT/" + docNO + ".txt");
            writer.write(currentDoc);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 

        //creating metadata file and writing metadata to file
        File dataFile = new File(exactFilePath + "/METADATA", docNO + ".txt");
        try {
            dataFile.createNewFile();
        } catch (IOException e) {
            System.err.println("Error while creating METADATA file: " + e.getMessage());
        }
        try {
            FileWriter writer = new FileWriter(exactFilePath + "/METADATA/" + docNO + ".txt");
            writer.write(docNO + "\n" + internalID + "\n" + date + "\n" + headline);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    // tokenizer method, allText input, arraylist of string tokens output (returns: ArrayList<String>)
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

    // tokens to ids method, reads in token list of strings and lexicon and returns list of integers which it gets from lexicon (returns: ArrayList<Integer>)
    public static ArrayList<Integer> convertTokensToIDs (ArrayList<String> tokens, HashMap<String, Integer> lexicon, ArrayList<String> lexiconWords) {
        ArrayList<Integer> tokenIDs = new ArrayList<>();
        for (String token : tokens) {
            if(lexicon.containsKey(token)) {
                tokenIDs.add(lexicon.get(token));
            } else {
                int i = lexicon.size();
                lexicon.put(token, i);
                tokenIDs.add(i);
                lexiconWords.add(token);
            }
        }
        return tokenIDs;
    }

    // countWords method to read in tokenID list and return hashmap or list of termID to count
    public static HashMap<Integer, Integer> countWords (ArrayList<Integer> tokenIDs) {
        HashMap<Integer, Integer> wordCounts = new HashMap<>();

        for(int id : tokenIDs) {
            if(wordCounts.containsKey(id)) {
                int count = wordCounts.get(id) + 1;
                wordCounts.put(id, count);
            } else {
                wordCounts.put(id, 1);
            }
        }

        return wordCounts;
    }

    // addToPostings reads in wordCounts, docID, and inverted index
    // for each word in wordCounts checks if term id entry exists, if not adds it, then goes to term id posting list and adds docID and count
    public static void addToPostings(HashMap<Integer, Integer> wordCounts, int docID, HashMap<Integer, ArrayList<Integer>> invertedIndex) {

        // for each unique token in the current doc, add this docID and count to its posting list in inverted index
        for (Map.Entry<Integer, Integer> id : wordCounts.entrySet()) {

            // if term is not in inverted index, adds it and its posting list
            if (!invertedIndex.containsKey(id.getKey())) {
                ArrayList<Integer> postingList = new ArrayList<>();
                invertedIndex.put(id.getKey(), postingList);
            }

            // access posting list at that term id
            ArrayList<Integer> postingList = invertedIndex.get(id.getKey());

            // adding docID and count to that posting list
            postingList.add(docID);
            postingList.add(id.getValue());
        }

    }

    // method to save lexicon mappings to a file in the same way as DOCNO, line in file = termID + 1
    // can do at the end and just save all mappings to lexicon.txt
    public static void saveLexicon(ArrayList<String> lexiconWords, String exportPath) {
        try {
            FileWriter writer = new FileWriter(exportPath + "/lexicon.txt", true);
            
            for (int i = 0; i < lexiconWords.size(); i++) {
                writer.write(lexiconWords.get(i) + "\n");
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    // method to save inverted index to .txt file, with key in first row then value (list) in next row, each element with a space
    public static void saveInvertedIndex(HashMap<Integer, ArrayList<Integer>> invertedIndex, String exportPath) {
        try {
            FileWriter writer = new FileWriter(exportPath + "/inverted-index.txt", true);
            
            for (Map.Entry<Integer, ArrayList<Integer>> entry : invertedIndex.entrySet()) {
                writer.write(entry.getKey() + "\n");
                ArrayList<Integer> value = entry.getValue();
                for (int num : value) {
                    writer.write(num + " ");
                }
                writer.write("\n");
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
}