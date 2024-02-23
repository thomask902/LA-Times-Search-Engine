# msci-541-f23-hw5-thomask902

**Thomas Kleinknecht, WATIAM: tkleinkn**

### Language information:
The programs were written in Java, specifically java version "13.0.1”. 

### Instructions to run:
In order to build and run the program, the program will need to be cloned from the repository, and can be run in the command line/terminal if you have java downloaded on your computer (or after downloading). 

First, ensure you have the proper directories and files set up. You will need the gzipped LA Times portion of the TREC volumes 4 and 5 collection downloaded, and its file path. Also, you will need to create an “latimes-index” directory and note the file path.

**IndexEngine**

IndexEngine must be run before BM25SearchEngine. In order to run IndexEngine, navigate to the cloned repository, and compile the code using the command:

javac IndexEngine.java

Now you can run IndexEngine with following command:

java IndexEngine “/path/to/latimes.gz” “path/to/latimes-index”

These two arguments should be enclosed in quotations, and contain the file path of your gzipped latimes file and your latimes-index directory for the un-stemmed collection.

This program may take a few minutes to run, so please be patient. When this is complete, you can now run the BM25SearchEngine program to make queries and perform retrieval based on the BM25 scoring system.

**BM25SearchEngine**

Before compiling BM25, ensure you know the location of your "latimes-index" directory.

To compile the code, do the following:

javac BM25SearchEngine.java

java BM25SearchEngine "/Path/to/your/latimes-index" 

The file path should be enclosed in quotations in case there are spaces.

The program may also take a few minutes to run initially, as it is loading in the data needed in order to perform queries. Once it has loaded, follow the prompts in the terminal to make queries, see results, and quit with the "Q" command when you are done!

**Thank you for visiting!**
