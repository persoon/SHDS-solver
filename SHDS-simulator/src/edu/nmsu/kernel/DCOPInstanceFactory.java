/*
 * Copyright (c) 2015.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.nmsu.kernel;

import edu.nmsu.Home.Home;
import edu.nmsu.Home.Rules.SchedulingRule;
import edu.nmsu.agents.MGM.MGMAgentState;
import edu.nmsu.problem.Parameters;
import edu.nmsu.problem.Utilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by ffiorett on 7/7/15
 * This is not a real Factory, but more an importer.
 * The term Factory was used for consistency
 */
public class DCOPInstanceFactory {

    public enum Type {xcsp, dzink, usc, smartHomeRules};

    public static DCOPInstance importDCOPInstance(String filename) {
        return importDCOPInstance(filename, Type.smartHomeRules);
    }

    public static DCOPInstance importDCOPInstance(String filename, Type type) {

        String ext = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            ext = filename.substring(i+1);
        }

        if (ext.equalsIgnoreCase("xcsp") || ext.equalsIgnoreCase("xml") || type == Type.xcsp) {
            return createXCSPInstance(filename);
        } else if (ext.equalsIgnoreCase("usc") || type == Type.usc) {
            return createUSCInstance(filename);
        } else if (ext.equalsIgnoreCase("dzn") || type == Type.dzink) {
            return createDZINCInstance(filename);
        } else if (type == Type.smartHomeRules) {
            return createSHDSInstance(filename);
        }

        return null;
    }

    private static DCOPInstance createXCSPInstance(String filename) {

        DCOPInstance instance = new DCOPInstance();

        try {
            File fXmlFile = new File(filename);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            doc.getDocumentElement().normalize();

            // Presentation
            NodeList presentation = doc.getElementsByTagName("presentation");
            String maximizeStr = presentation.item(0).getAttributes().getNamedItem("maximize").getNodeValue();
            Boolean maximize = Boolean.valueOf(maximizeStr);
            instance.setOptimization(maximize);

            int optType = maximize ? Constants.OPT_MAXIMIZE : Constants.OPT_MINIMIZE;

            // Agents
            NodeList agents = doc.getElementsByTagName("agent");
            for (int i = 0; i < agents.getLength(); i++) {
                Node nodeAgent = agents.item(i);
                String name = nodeAgent.getAttributes().getNamedItem("name").getNodeValue();

                // Create and store Agent in DCOP instance
                instance.addAgent(new AgentState(name, i));
            }

            // Variables
            NodeList variables = doc.getElementsByTagName("variable");
            for (int i = 0; i < variables.getLength(); i++) {
                Node nodeVariable = variables.item(i);
                String name = nodeVariable.getAttributes().getNamedItem("name").getNodeValue();
                String domainName = nodeVariable.getAttributes().getNamedItem("domain").getNodeValue();
                String agentName = nodeVariable.getAttributes().getNamedItem("agent").getNodeValue();

                // Get domain
                Node domainNode = getXMLNode(doc, "domain", domainName);
                String[] valuesStr = domainNode.getTextContent().split(Pattern.quote(".."));
                int min = Integer.valueOf(valuesStr[0]);
                int max = Integer.valueOf(valuesStr[1]);

                // Create and store Variable in DCOP instance
                AgentState agtOwner = instance.getAgent(agentName);
                Variable variable = VariableFactory.getVariable(name, min, max, "INT-BOUND", agtOwner);
                instance.addVariable(variable);
            }

            // Constraints
            NodeList constraints = doc.getElementsByTagName("constraint");
            for (int i = 0; i < constraints.getLength(); i++) {
                Node constraintNode = constraints.item(i);
                String name = constraintNode.getAttributes().getNamedItem("name").getNodeValue();
                int arity = Integer.valueOf(constraintNode.getAttributes().getNamedItem("arity").getNodeValue());
                String[] scopeStr = constraintNode.getAttributes().getNamedItem("scope").getTextContent().split(" ");
                String relName = constraintNode.getAttributes().getNamedItem("reference").getNodeValue();

                // Retrieve scope:
                ArrayList<Variable> scope = new ArrayList<Variable>();
                for (String s : scopeStr) {
                    scope.add(instance.getVariable(s));
                }

                // Get Relation
                Node relationNode = getXMLNode(doc, "relation", relName);
                String defCostStr = relationNode.getAttributes().getNamedItem("defaultCost").getNodeValue();
                int defaultValue = 0;
                if (defCostStr.equalsIgnoreCase("infinity"))
                    defaultValue = Constants.infinity;
                else if (defCostStr.equalsIgnoreCase("-infinity"))
                    defaultValue = -Constants.infinity;
                else
                    defaultValue = Integer.valueOf(defCostStr);
                String semantics = relationNode.getAttributes().getNamedItem("semantics").getNodeValue();

                // Create constraint
                Constraint constraint = ConstraintFactory.getConstraint(name, scope, defaultValue, semantics);

                // Add values
                int values[] = new int[arity];
                String[] valuesStr = relationNode.getTextContent().split(Pattern.quote("|"));

                for (String s : valuesStr) {
                    String costValue[] = s.split(Pattern.quote(":"));
                    String utilStr = costValue[0];
                    int utility =
                            utilStr.equalsIgnoreCase("infinity") ? Constants.infinity
                                    : utilStr.equalsIgnoreCase("-infinity") ? -Constants.infinity
                                    : Integer.valueOf(utilStr);
                    String tupleStr[] = costValue[1].split(Pattern.quote(" "));
                    assert (tupleStr.length == arity);
                    for (int t = 0; t < arity; t++) {
                        values[t] = Integer.valueOf(tupleStr[t]);
                    }
                    constraint.addValue(new Tuple(values), utility, optType);
                }

                // Store Constraint in DCOP instance
                instance.addConstraint(constraint);
            }

            return instance;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get the XMLNode of the given categroy matching the given name.
     * @param doc The XML document.
     * @param tag The tag to match.
     * @param name The name to match.
     * @return
     */
    private static Node getXMLNode(Document doc, String tag, String name) {
        NodeList nlist = doc.getElementsByTagName(tag);
        for (int i = 0; i < nlist.getLength(); i++) {
            Node node = nlist.item(i);
            String nodeName = node.getAttributes().getNamedItem("name").getNodeValue();
            if (nodeName.equalsIgnoreCase(name)) {
                return node;
            }
        }
        return null;
    }


    private static DCOPInstance createSHDSInstance(String filename) {

        DCOPInstance instance = new DCOPInstance();
        Map<String, JSONArray> mapNeighbors = new HashMap<>();

        String content = null;
        try {
            content = Utilities.readFile(filename);
            JSONObject jObject  = new JSONObject(content.trim());
            int horizon = jObject.getInt("horizon");
            Parameters.setHorizon(horizon);
            //Parameters.setGranularity(jObject.getInt("granularity"));
            int fileGran = jObject.getInt("granularity");
            /*
            JSONArray jPriceSchema = jObject.getJSONArray("priceSchema");
            double[] priceSchema = new double[horizon];
            for(int i = 0; i < horizon; i++) {
                priceSchema[i] = jPriceSchema.getDouble(i);
            }
            */
            //Parameters.setPriceSchema(priceSchema);

            JSONObject jAgents = jObject.getJSONObject("agents");
            Iterator<?> keys = jAgents.keys();

            int agt_id = 0;
            while( keys.hasNext() ) {
                String name = (String)keys.next();
                if ( jAgents.get(name) instanceof JSONObject ) {
                    JSONObject jAgent = jAgents.getJSONObject(name);

                    // Background loads
                    JSONArray jBgLoad = jAgent.getJSONArray("backgroundLoad");
                    double[] bgLoads = new double[horizon];

                    /*
                    for (int i = 0; i < horizon; i++) { // <--- .length before this is a quick fix for now... 5_30_2017 If the granularity in file is higher than what you scale it to, you end up with some timesteps with null loads. TODO method that takes in a double array and an integer to scale it down or up
                        bgLoads[i] = jBgLoad.getDouble(i);
                    }*/

                    for (int i = 0; i < horizon; i++) { // using this for testing so we have consistent background loads across all files ^^^above is original one^^^
                        bgLoads[i] = 0;
                    }

                    // Create Home
                    Home home = new Home(name, jAgent.getInt("houseType"));

                    // Load Rules
                    JSONArray jRules = jAgent.getJSONArray("rules");
                    for (int r = 0; r < jRules.length(); r++) {
                        SchedulingRule rule = new SchedulingRule(jRules.getString(r), home.getName());
                        //This is so we can test granularity scalability without generating new ones for ever single granularity
                        //multiply everything by file granularity to get the time in minutes, divide by new granularity
                        if(fileGran != Parameters.getGran()) {
                            //System.out.println(rule.getTimePrefix() + " " + Parameters.getGran() + ": [" + rule.getTimeStart() + "," +rule.getTimeEnd() +
                            //        "]->[" + rule.getTimeStart() * fileGran / Parameters.getGran() + "," + rule.getTimeEnd()   * fileGran / Parameters.getGran() + "]");
                            rule.setTimeStart(rule.getTimeStart() * fileGran / Parameters.getGran());
                            rule.setTimeEnd  (rule.getTimeEnd()   * fileGran / Parameters.getGran());
                        }

                        //if(rule.getTimeEnd() == 12) System.out.println("_________________________\nRULE ENDS WITH 12"+ jRules.getString(r) +"\n_________________________");
                        home.addRule( rule );
                    }
                    home.activatePassiveRules();

                    // Create and store Agent in DCOP instance
                    MGMAgentState state = new MGMAgentState(name, agt_id++, home, bgLoads);
                    instance.addAgent(state);

                    // save Neighbors
                    JSONArray jNeighbors = jAgent.getJSONArray("neighbors");
                    mapNeighbors.put(name, jNeighbors);
                }
            }

            // Link Neighbors
            for (AgentState agtState : instance.getDCOPAgents()) {
                JSONArray jNeighbors = mapNeighbors.get(agtState.getName());
                for (int i = 0; i < jNeighbors.length(); i++) {
                    AgentState neighbor = instance.getAgent(jNeighbors.getString(i));
                    agtState.registerNeighbor(neighbor);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return instance;
    }


    private static DCOPInstance createUSCInstance(String filename) {
        return null;
    }

    private static DCOPInstance createDZINCInstance(String filename) {
        return null;
    }

}




