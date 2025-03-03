// BSD License (http://www.galagosearch.org/license)

package org.galagosearch.core.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.galagosearch.core.parse.Document;
import org.galagosearch.core.tools.Search.SearchResult;
import org.galagosearch.core.tools.Search.SearchResultItem;
import org.galagosearch.tupleflow.Parameters;
import org.galagosearch.tupleflow.Utility;
import org.mortbay.jetty.handler.AbstractHandler;
import org.w3c.dom.NodeList;
import org.znerd.xmlenc.XMLOutputter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

class RankAlg{
    double b, k_1, k_2; //new
    public RankAlg(){
    	setValues();
    }
    public void randomize(){
    	b += (Math.random() - .5) * 10;
    	k_1 += (Math.random() -.5) * 10;
    	k_2 += (Math.random() - .5) * 20;
    }
    public void setValues(){
    	try  {
    		BufferedReader br = new BufferedReader(new FileReader("values.txt"));
			b = Double.parseDouble(br.readLine().split(" ")[2]);
    		k_1 = Double.parseDouble(br.readLine().split(" ")[2]);
    		k_2 = Double.parseDouble(br.readLine().split(" ")[2]);
    		br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    public void writeValues(){
    	try{
    	    PrintWriter writer = new PrintWriter("values.txt", "UTF-8");
    	    writer.println("b = " + b);
    	    System.out.println("making b " + b);
    	    writer.println("k_1 = " + k_1);
    	    writer.println("k_2 = " + k_2);
    	    writer.close();
    	} catch (IOException e) {
    	   // do something
    	}
    }
    public void printFile(){
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	try {
    		DocumentBuilder builder = factory.newDocumentBuilder();
    		String path = new File(System.getProperty("user.dir")).getParent();
    		String manifest = path + File.separator + "corpus" + 
					File.separator + "wiki-small.index" + File.separator + "manifest.xml";
			BufferedReader br = new BufferedReader(new FileReader(path + File.separator + "corpus" + 
					File.separator + "wiki-small.index" + File.separator + "manifest.xml"));
			System.out.println(br.readLine());
			br.close();
			org.w3c.dom.Document doc = builder.parse(manifest);
			NodeList nl = doc.getElementsByTagName("b");
			System.out.println("b is equivalent to " + nl);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }
    public Parameters getParameters(){
    	Parameters values = new Parameters();
    	values.add("b", Double.toString(b));
    	values.add("k_1", Double.toString(k_1));
    	return values;
    }
}
/**
 * <p>Handles web search requests against a Galago index.  Also handles XML requests for
 * documents, snippets and search results.</p>
 *
 * <p>This class is set up to work with an embedded Jetty instance, but it should be
 * fairly easy to wrap into a Servlet for use with something else (Tomcat, Glassfish, etc.)</p>
 *
 * <p>URLs supported:</p>
 *
 * <table>
 *   <tr>
 *     <td>/</td>
 *     <td>Main Page</td>
 *   </tr>
 *   <tr>
 *     <td>/search</td>
 *     <td>HTML Search Results (q, start, n)</td>
 *   </tr>
 *   <tr>
 *     <td>/xmlsearch</td>
 *     <td>XML Search Results (q, start, n)</td>
 *   </tr>
 *   <tr>
 *     <td>/snippet</td>
 *     <td>XML Snippet Result (identifier, term+)</td>
 *   </tr>
 *   <tr>
 *     <td>/document</td>
 *     <td>Document Result (identifier)</td>
 *   </tr>
 * </table>
 *
 * @author trevor
 */
public class SearchWebHandler extends AbstractHandler {
    Search search;
    ArrayList<RankAlg> alg;

    public SearchWebHandler(Search search) {
        this.search = search;
        
    }

    public String getEscapedString(String text) {
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c >= 128) {
                builder.append("&#" + (int)c + ";");
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    public void handleDocument(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.getParameterMap();
        String identifier = request.getParameter("identifier");
        System.out.println("Rank Alg is" + request.getParameter("rankalg"));
        alg.get(Integer.parseInt(request.getParameter("rankalg"))).writeValues();
        Document document = search.getDocument(identifier);
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(getEscapedString(document.text));
        writer.close();
    }

    public void handleSnippet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String identifier = request.getParameter("identifier");
        String[] terms = request.getParameterValues("term");
        Set<String> queryTerms = new HashSet<String>(Arrays.asList(terms));

        Document document = search.getDocument(identifier);

        if (document == null) {
            response.setStatus(response.SC_NOT_FOUND);
        } else {
            response.setContentType("text/xml");
            PrintWriter writer = response.getWriter();
            String snippet = search.getSummary(document, queryTerms);
            String title = document.metadata.get("title");
            String url = document.metadata.get("url");

            if (snippet == null) snippet = "";

            response.setContentType("text/xml");
            writer.append("<response>\n");
            writer.append(String.format("<snippet>%s</snippet>\n", snippet));
            writer.append(String.format("<identifier>%s</identifier>\n", identifier));
            writer.append(String.format("<title>%s</title>\n", scrub(title)));
            writer.append(String.format("<url>%s</url>\n", scrub(url)));
            writer.append("</response>");
            writer.close();
        }
    }

    private String scrub(String s) throws UnsupportedEncodingException {
        if (s == null) return s;
        return s.replace("<", "&gt;")
                .replace(">", "&lt;")
                .replace("&", "&amp;");
    }

    public void retrieveImage(OutputStream output) throws IOException {
        InputStream image = getClass().getResourceAsStream("/images/galago.png");
        Utility.copyStream(image, output);
        output.close();
    }

    public void handleImage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OutputStream output = response.getOutputStream();
        response.setContentType("image/png");
        retrieveImage(output);
    }
    public void renderLink(SearchResultItem item, PrintWriter writer) throws UnsupportedEncodingException, Exception{
    	writer.append("<div id=\"result\">\n");
        writer.append(String.format("<a href=\"document?identifier=%s&rankalg=%s\">%s</a><br/>" +
                                    "<div id=\"summary\">%s</div>\n" +
                                    "<div id=\"meta\">%s - %s</div>\n",
                                    item.identifier,
                                    item.alg,
                                    item.displayTitle,
                                    item.summary,
                                    scrub(item.identifier),
                                    scrub(item.url)));
        writer.append("</div>\n");
    }
    public SearchResult mergeResults(SearchResult[] result){
    	SearchResult retVal = new SearchResult();
    	retVal.items = new ArrayList<SearchResultItem>();
    	retVal.query = result[0].query;
    	retVal.transformedQuery = result[0].transformedQuery;
    	for (int i = 0; i < 3; i++){
    		for (SearchResultItem item : result[i].items){
    			item.alg = i;
    		}
    	}
    	for (int rank = 0; rank < result[0].items.size(); rank++){
    		ArrayList<SearchResult> liResult = new ArrayList<SearchResult>();
    		for (int j = 0; j < 3; j++){
    			liResult.add(result[j]);
    		}
    		for (int j = 0; j < 3; j++){
    			Random rand = new Random();
    			int randNum = rand.nextInt(liResult.size());
    			SearchResultItem addingItem = liResult.get(randNum).items.get(rank);
    			boolean add = true;
    			for (SearchResultItem item : retVal.items){
    				if (addingItem.identifier.equals(item.identifier)){
    					add = false;
    					break;
    				}
    			}
    			if(add){
    				retVal.items.add(liResult.get(randNum).items.get(rank));
    			}
    			liResult.remove(randNum);
    		}
    	}
    	System.out.println("Produced " + retVal.items.size() + " items");
    	return retVal;
    }

    public void handleSearch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        alg = new ArrayList<RankAlg>();
        for(int i = 0; i < 3; i++){
        	alg.add(new RankAlg());
        }
        alg.get(1).randomize();
        alg.get(2).randomize();
        SearchResult[] indResults = new SearchResult[3];
        for(int i = 0; i < 3; i++){
        	indResults[i] = performSearch(request, alg.get(i).getParameters());
        }
        SearchResult result = mergeResults(indResults);
        response.setContentType("text/html");
        String displayQuery = scrub(request.getParameter("q"));
        String encodedQuery = URLEncoder.encode(request.getParameter("q"), "UTF-8");

        PrintWriter writer = response.getWriter();
        writer.append("<html>\n");
        writer.append("<head>\n");
        writer.append(String.format("<title>%s - Galago Search</title>\n", displayQuery));
        writeStyle(writer);
        writer.append("<script type=\"text/javascript\">\n");
        writer.append("function toggleDebug() {\n");
        writer.append("   var object = document.getElementById('debug');\n");
        writer.append("   if (object.style.display != 'block') {\n");
        writer.append("     object.style.display = 'block';\n");
        writer.append("  } else {\n");
        writer.append("     object.style.display = 'none';\n");
        writer.append("  }\n");
        writer.append("}\n");
        writer.append("</script>\n");
        writer.append("</head>\n<body>\n");

        writer.append("<div id=\"header\">\n");
        writer.append("<table><tr>");
        writer.append("<td><a href=\"http://www.galagosearch.org\">" +
                      "<img src=\"/images/galago.png\"></a></td>");
        writer.append("<td><br/><form action=\"search\">" +
                      String.format("<input name=\"q\" size=\"40\" value=\"%s\" />", displayQuery) +
                      "<input value=\"Search\" type=\"submit\" /></form></td>");
        writer.append("</tr>");
        writer.append("</table>\n");
        writer.append("</div>\n");

        writer.append("<center>[<a href=\"#\" onClick=\"toggleDebug(); return false;\">debug</a>]</center>");
        writer.append("<div id=\"debug\">");
        writer.append("<table>");
        writer.append(String.format("<tr><td>%s</td><td>%s</td></tr>",
                      "Parsed Query", result.query.toString()));
        writer.append(String.format("<tr><td>%s</td><td>%s</td></tr>",
                      "Transformed Query", result.transformedQuery.toString()));
        writer.append("</table>");
        writer.append("</div>");
        for (SearchResultItem item : result.items) {
            renderLink(item, writer);
        }

        String startAtString = request.getParameter("start");
        String countString = request.getParameter("n");
        int startAt = 0;
        int count = 10;

        if (startAtString != null) {
            startAt = Integer.parseInt(startAtString);
        }
        if (countString != null) {
            count = Integer.parseInt(countString);
        }

        writer.append("<center>\n");
        if (startAt != 0) {
            writer.append(String.format("<a href=\"search?q=%s&start=%d&n=%d\">Previous</a>",
                                        encodedQuery, Math.max(startAt-count,0), count));
            if (result.items.size() >= count) {
                writer.append(" | ");
            }
        }

        if (result.items.size() >= count) {
            writer.append(String.format("<a href=\"search?q=%s&start=%d&n=%d\">Next</a>",
                                        encodedQuery, startAt+count, count));
        }
        writer.append("</center>");
        writer.append("</body>");
        writer.append("</html>");
        writer.close();
    }

    public void handleSearchXML(HttpServletRequest request, HttpServletResponse response) throws IllegalStateException, IllegalArgumentException, IOException, Exception {
        SearchResult result = performSearch(request, null);
        PrintWriter writer = response.getWriter();
        XMLOutputter outputter = new XMLOutputter(writer, "UTF-8");
        response.setContentType("text/xml");

        outputter.startTag("response");

        writer.append("<response>\n");
        for (SearchResultItem item : result.items) {
            outputter.startTag("result");
            
            outputter.startTag("identifier");
            outputter.pcdata(item.identifier);
            outputter.endTag();
            
            outputter.startTag("title");
            outputter.pcdata(item.displayTitle);
            outputter.endTag();

            outputter.startTag("url");
            outputter.pcdata(item.url);
            outputter.endTag();

            outputter.startTag("snippet");
            outputter.pcdata(item.summary);
            outputter.endTag();

            outputter.startTag("rank");
            outputter.pcdata("" + item.rank);
            outputter.endTag();
            
            outputter.startTag("metadata");
            for (Entry<String, String> entry : item.metadata.entrySet()) {
                outputter.startTag("item");
                outputter.startTag("key");
                outputter.pcdata(entry.getKey());
                outputter.endTag();
                outputter.startTag("value");
                outputter.pcdata(entry.getValue());
                outputter.endTag();
            }
            
            outputter.endTag();
        }
    }

    public void writeStyle(PrintWriter writer) {
        writer.write("<style type=\"text/css\">\n");
        writer.write("body { font-family: Helvetica, sans-serif; }\n");
        writer.write("img { border-style: none; }\n");
        writer.write("#box { border: 1px solid #ccc; margin: 100px auto; width: 500px;" +
                     "background: rgb(210, 233, 217); }\n");
        writer.write("#box a { font-size: small; text-decoration: none; }\n");
        writer.write("#box a:link { color: rgb(0, 93, 40); }\n");
        writer.write("#box a:visited { color: rgb(90, 93, 90); }\n");
        writer.write("#header { background: rgb(210, 233, 217); border: 1px solid #ccc; }\n");
        writer.write("#result { padding: 10px 5px; max-width: 550px; }\n");
        writer.write("#meta { font-size: small; color: rgb(60, 100, 60); }\n");
        writer.write("#summary { font-size: small; }\n");
        writer.write("#debug { display: none; }\n");
        writer.write("</style>");
    }

    public void handleMainPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();
        response.setContentType("text/html");

        writer.append("<html>\n");
        writer.append("<head>\n");
        writeStyle(writer);
        writer.append("<title>Galago Search</title></head>");
        writer.append("<body>");
        writer.append("<center><br/><br/><div id=\"box\">" +
                      "<a href=\"http://www.galagosearch.org\">" +
                      "<img src=\"/images/galago.png\"/></a><br/>\n");
        writer.append("<form action=\"search\"><input name=\"q\" size=\"40\">" +
                      "<input value=\"Search\" type=\"submit\" /></form><br/><br/>");
        writer.append("</div></center></body></html>\n");
        writer.close();
    }

    public void handle(String target,
            HttpServletRequest request,
            HttpServletResponse response,
            int dispatch) throws IOException, ServletException {
        if (request.getPathInfo().equals("/search")) {
            try {
                handleSearch(request, response);
            } catch(Exception e) {
                throw new ServletException("Caught exception from handleSearch", e);
            }
        } else if (request.getPathInfo().equals("/document")) {
            handleDocument(request, response);
        } else if (request.getPathInfo().equals("/searchxml")) {
            try {
                handleSearchXML(request, response);
            } catch(Exception e) {
                throw new ServletException("Caught exception from handleSearchXML", e);
            }
        } else if (request.getPathInfo().equals("/snippet")) {
            handleSnippet(request, response);
        } else if (request.getPathInfo().startsWith("/images")) {
            handleImage(request, response);
        } else {
            handleMainPage(request, response);
        }
    }

    private SearchResult performSearch(HttpServletRequest request, Parameters parameters) throws Exception {
        String query = request.getParameter("q");
        //String startAtString = request.getParameter("start");
        //String countString = request.getParameter("n");
        //int startAt = (startAtString == null) ? 0 : Integer.parseInt(startAtString);
        //int resultCount = (countString == null) ? 10 : Integer.parseInt(countString);
        SearchResult result = search.runQuery(query, parameters, true);
        return result;
    }
}
