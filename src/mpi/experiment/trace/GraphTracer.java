package mpi.experiment.trace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import mpi.tools.javatools.parsers.Char;
import mpi.aida.access.DataAccess;
import mpi.aida.data.EntityMetaData;
import mpi.aida.util.CollectionUtils;

import org.apache.commons.lang.StringUtils;

public class GraphTracer {
	public enum TracingTarget {
		STATIC, WEB_INTERFACE
	};

	// To keep the initial graph nodes
	private Map<String, Map<String, List<TracingEntity>>> docMentionCandidatesOriginalMap = new HashMap<String, Map<String, List<TracingEntity>>>();

	// To keep entities remaining after applying distance constraint
	private Map<String, Map<String, List<TracingEntity>>> docMentionCandidatesCleanedMap = new HashMap<String, Map<String, List<TracingEntity>>>();

	// To keep entities remaining after running the graph algorithm
	private Map<String, Map<String, List<TracingEntity>>> docMentionCandidatesFinalMap = new HashMap<String, Map<String, List<TracingEntity>>>();

	private Map<String, List<TracingEntity>> docRemovedEntities = new HashMap<String, List<TracingEntity>>();

	private Map<String, Map<String, String>> docStats = new HashMap<String, Map<String, String>>();

	private Map<String, List<String>> docStatsOrder = new HashMap<String, List<String>>();

	private Map<String, Set<String>> docLocalOnlyMentions = new HashMap<String, Set<String>>();

	 private Map<String, Set<String>> docDanglingMentions = new HashMap<String, Set<String>>();
	
  private Map<String, Set<String>> docLocalIncludingPriorMentions = new HashMap<String, Set<String>>();

  public static GraphTracer gTracer = new NullGraphTracer();
  
	public void addCandidateEntityToOriginalGraph(String docId, String mention,
			String candidateEntity, double entityWeightedDegree,
			double MESimilairty, Map<String, Double> connectedEntities) {
		Map<String, List<TracingEntity>> mentionCandidatesOriginalMap = docMentionCandidatesOriginalMap
				.get(docId);
		if (mentionCandidatesOriginalMap == null) {
			mentionCandidatesOriginalMap = new HashMap<String, List<TracingEntity>>();
			docMentionCandidatesOriginalMap.put(docId,
					mentionCandidatesOriginalMap);
		}
		addCandidateToMentionsMap(mentionCandidatesOriginalMap, mention,
				candidateEntity, entityWeightedDegree, MESimilairty,
				connectedEntities);
	}

	public void addCandidateEntityToCleanedGraph(String docId, String mention,
			String candidateEntity, double entityWeightedDegree,
			double MESimilairty) {
		Map<String, List<TracingEntity>> mentionCandidatesCleanedMap = docMentionCandidatesCleanedMap
				.get(docId);
		if (mentionCandidatesCleanedMap == null) {
			mentionCandidatesCleanedMap = new HashMap<String, List<TracingEntity>>();
			docMentionCandidatesCleanedMap.put(docId,
					mentionCandidatesCleanedMap);
		}
		addCandidateToMentionsMap(mentionCandidatesCleanedMap, mention,
				candidateEntity, entityWeightedDegree, MESimilairty, null);
	}

	public void addCandidateEntityToFinalGraph(String docId, String mention,
			String candidateEntity, double entityWeightedDegree,
			double MESimilairty) {
		Map<String, List<TracingEntity>> mentionCandidatesFinalMap = docMentionCandidatesFinalMap
				.get(docId);
		if (mentionCandidatesFinalMap == null) {
			mentionCandidatesFinalMap = new HashMap<String, List<TracingEntity>>();
			docMentionCandidatesFinalMap.put(docId, mentionCandidatesFinalMap);
		}
		addCandidateToMentionsMap(mentionCandidatesFinalMap, mention,
				candidateEntity, entityWeightedDegree, MESimilairty, null);
	}

	public void addEntityRemovalStep(String docId, String entity,
			double entityWeightedDegree, List<String> connectedMentions) {
		List<TracingEntity> removedEntities = docRemovedEntities.get(docId);
		if (removedEntities == null) {
			removedEntities = new ArrayList<TracingEntity>();
			docRemovedEntities.put(docId, removedEntities);
		}
		TracingEntity te = new TracingEntity(entity, entityWeightedDegree,
				connectedMentions);
		removedEntities.add(te);
	}
	
	 public void addMentionToLocalIncludingPrior(String docId, String mention,
	      int mentionOffset) {
      Set<String> localIncludingPriorMentions = docLocalIncludingPriorMentions.get(docId);
	    if (localIncludingPriorMentions == null) {
	      localIncludingPriorMentions = new HashSet<String>();
	      docLocalIncludingPriorMentions.put(docId, localIncludingPriorMentions);
	    }
	    localIncludingPriorMentions.add(mentionOffset + ": " + mention);
	  }

	public void addMentionToLocalOnly(String docId, String mention,
			int mentionOffset) {
	  Set<String> localOnlyMentions = docLocalOnlyMentions.get(docId);
		if (localOnlyMentions == null) {
			localOnlyMentions = new HashSet<String>();
			docLocalOnlyMentions.put(docId, localOnlyMentions);
		}
		localOnlyMentions.add(mentionOffset + ": " + mention);
	}
	
	 public void addMentionToDangling(String docId, String mention,
	      int mentionOffset) {
	    Set<String> danglingMentions = docDanglingMentions.get(docId);
	    if (danglingMentions == null) {
	      danglingMentions = new HashSet<String>();
	      docDanglingMentions.put(docId, danglingMentions);
	    }
	    danglingMentions.add(mentionOffset + ": " + mention);
	  }
	
	public void addStat(String docId, String description, String value) {
		Map<String, String> stats = docStats.get(docId);

		if (stats == null) {
			stats = new HashMap<String, String>();
			docStats.put(docId, stats);
		}

		stats.put(description, value);

		List<String> order = docStatsOrder.get(docId);

		if (order == null) {
			order = new LinkedList<String>();
			docStatsOrder.put(docId, order);
		}

		order.add(description);
	}

	public String generateScript() {
		StringBuilder sb = new StringBuilder();
		sb.append("<script type='text/javascript'>\n");
		sb.append("function showHide(id) {\n");
		sb.append("var checkboxElement = document.getElementById( id + '-checkbox');\n");
		sb.append("var divElement = document.getElementById( id + '-div');\n");
		sb.append("if (checkboxElement.checked) {\n");
		sb.append("divElement.style.display = 'block';\n");
		sb.append("} else {\n");
		sb.append("divElement.style.display = 'none';\n");
		sb.append("}\n");
		sb.append("}\n");
		sb.append("function setVisibility(id, visibility) {document.getElementById(id).style.display = visibility;}");
		sb.append("</script>\n");
		return sb.toString();
	}

	public String generateHtml(String docId, TracingTarget tracingTarget) {
		StringBuilder sb = new StringBuilder();
		sb.append(generateScript());
		Map<String, String> stats = docStats.get(docId);
		List<String> order = docStatsOrder.get(docId);
		if (stats != null) {
			sb.append("<div id='run_info'>");
			if(tracingTarget == TracingTarget.STATIC) {
				sb.append("<h1>Run Information</h1>\n");	
			}

			sb.append("<ul>\n");
			for (String desc : order) {
				sb.append("<li><span style='font-weight:bold'>" + desc
						+ ":</span> " + stats.get(desc) + "</li>\n");
			}
      sb.append("</ul>");

      if (docLocalIncludingPriorMentions.get(docId) != null) {
        sb.append("<p><strong>Mentions solved including prior in local sim: </strong>");
        sb.append(StringUtils.join(docLocalIncludingPriorMentions.get(docId), " ---- "));
        sb.append("</p>");
        sb.append("<p>Solved by prior in local in total: " + docLocalIncludingPriorMentions.get(docId).size() + "</p>");
      }
			
			if (docLocalOnlyMentions.get(docId) != null) {
				sb.append("<p><strong>Mentions solved by local sim. only: </strong>");
				sb.append(StringUtils.join(docLocalOnlyMentions.get(docId),
						" ---- "));
				sb.append("</p>");
				sb.append("<p>Solved by local only in total: " + docLocalOnlyMentions.get(docId).size() + "</p>");
			}
     if (docDanglingMentions.get(docId) != null) {
        sb.append("<p><strong>Unconnected mentions removed: </strong>");
        sb.append(StringUtils.join(docDanglingMentions.get(docId),
            " ---- "));
        sb.append("</p>");
        sb.append("<p>Dangling mentions in total: " + docDanglingMentions.get(docId).size() + "</p>");
      }
		}
		sb.append("</div>");
		sb.append("<div id='graph'>");
		switch (tracingTarget) {
		case STATIC:
			sb.append("<h1> Graph</h1>");
			sb.append(getColoredGraphOuput(docId));
			break;
		case WEB_INTERFACE:
			sb.append("<div id='graph_mentions'>");
			sb.append(getColoredGraphOuputForWebInterface(docId));
			sb.append("</div>");
		}
		sb.append("</div>");
		int counter = 1;
		sb.append("<div id='removal_steps'>");
		sb.append("<a name='removalSteps'></a>");
		if(tracingTarget == TracingTarget.STATIC) {
			sb.append("<h1> Removal Steps</h1>");
		}
		sb.append("<table>");
		List<TracingEntity> removedEntities = docRemovedEntities.get(docId);
		if (removedEntities != null) {
			for (TracingEntity tr : removedEntities) {
				sb.append("<tr>");
				sb.append("<td>");
				sb.append(counter++);
				sb.append("</td>");
				sb.append("<td>");
				sb.append("<strong>Entity Dropped: </strong>" + tr.entity
						+ " , <strong>Weighted Degree: </strong>"
						+ tr.weightedDegree + " <br />");
				sb.append("<strong>Connected Mentions: </strong>"
						+ StringUtils.join(tr.connectedMentions, ", "));
				sb.append("<br />");
				sb.append("<br />");
				sb.append("</td>");
				sb.append("</tr>");
			}
		}
		sb.append("</table>");
				
		sb.append("</div>");
		return sb.toString();
	}

  public void writeOutput(String outputPath, String docId)
			throws IOException, FileNotFoundException {
		cleanRemovalSteps(docId);
		File outFile = new File(outputPath + docId + "_graphtrace.html");
		String sb = generateScript() + ""
				+ generateHtml(docId, GraphTracer.TracingTarget.STATIC);
		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(outFile), "UTF-8"));
			writer.write(sb);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			for (int i = 0; i < 2; i++) {
				try {
					BufferedWriter writer = new BufferedWriter(
							new OutputStreamWriter(
									new FileOutputStream(outFile), "UTF-8"));
					writer.write(sb);
					writer.flush();
					writer.close();
				} catch (IOException ioe) {
				}
			}
			System.err.println("Couldn't write graph tracer for '" + docId
					+ "', skipping ...");
		}
	}

	public void writeOutput(String outputPath) throws IOException,
			FileNotFoundException {
		File outDir = new File(outputPath);
		if (!outDir.exists()) {
			outDir.mkdirs();
		}
		for (String docId : docMentionCandidatesOriginalMap.keySet()) {
			writeOutput(outputPath, docId);
		}
	}

	public boolean canGenerateHtmlFor(String docId) {
		return docMentionCandidatesOriginalMap.containsKey(docId);
	}

	public void removeDocId(String docId) {
		docMentionCandidatesOriginalMap.remove(docId);
		docMentionCandidatesCleanedMap.remove(docId);
		docMentionCandidatesFinalMap.remove(docId);
		docRemovedEntities.remove(docId);
		docStats.remove(docId);
		docStatsOrder.remove(docId);
		docLocalOnlyMentions.remove(docId);
	}

	/**
	 * Some removal steps aren't canceled later on by the algorithm because some
	 * intermediate stage was the optimal solution This methods check if the
	 * removed entity was still there in the final solution and hence it's a
	 * canceled removal step
	 * 
	 * @param docId
	 */
	public void cleanRemovalSteps(String docId) {
		Map<String, List<TracingEntity>> finalGraph = docMentionCandidatesFinalMap
				.get(docId);
		if (finalGraph == null) {
		  return;
		}
		Set<TracingEntity> remainingEntities = new HashSet<TracingEntity>();
		for (String mention : finalGraph.keySet()) {
			remainingEntities.addAll(finalGraph.get(mention));
		}
		List<TracingEntity> removalSteps = docRemovedEntities.get(docId);
		if (removalSteps == null)
			return;
		for (int i = removalSteps.size() - 1; i >= 0; i--) {
			TracingEntity removalStep = removalSteps.get(i);
			if (remainingEntities.contains(removalStep)) {
				removalSteps.remove(i);
			}
		}
	}

	private String getColoredGraphOuput(String docId) {
		List<String> mentions = new LinkedList<String>(
				docMentionCandidatesOriginalMap.get(docId).keySet());

		Collections.sort(mentions, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				int id1 = Integer.parseInt(o1.split(":::")[1]);
				int id2 = Integer.parseInt(o2.split(":::")[1]);

				return new Integer(id1).compareTo(id2);
			}
		});

		StringBuilder sb = new StringBuilder();
		for (String mention : mentions) {
			sb.append("<a href='#" + mention + "'>" + mention + "</a>  ----  ");
		}
		sb.append("<br /><a href='#removalSteps'> Removal Steps </a> <br /><br />");
		sb.append("<table>");
		for (String mention : mentions) {
			sb.append("<tr>");

			sb.append("<td style='vertical-align: top;'>");
			sb.append("<a name='" + mention + "'></a>");
			sb.append("<h2>" + mention + "</h2>");
			sb.append("</td>");
			sb.append("<td>");
			sb.append(getColoredMentionEntitiesOutput(docId, mention));
			sb.append("</td>");
			sb.append("</tr>");
		}
		sb.append("</table>");
		
		return sb.toString();

	}

  private String getColoredGraphOuputForWebInterface(String docId) {
		List<String> mentions = new LinkedList<String>(
				docMentionCandidatesOriginalMap.get(docId).keySet());

		Collections.sort(mentions, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				int id1 = Integer.parseInt(o1.split(":::")[1]);
				int id2 = Integer.parseInt(o2.split(":::")[1]);

				return new Integer(id1).compareTo(id2);
			}
		});

		StringBuilder sb = new StringBuilder();

		for (String mention : mentions) {
			String mentionParts[]  = mention.split(":::");
			String mentionStr = mentionParts[1] + ": " + mentionParts[0];
			String suffix = "";
			if (docLocalOnlyMentions.get(docId) != null
					&& docLocalOnlyMentions.get(docId).contains(mentionStr)) {
				suffix = " (solved by local sim. only)";
			}
			sb.append("<h3><a href=\"#\">" + mentionStr + suffix + "</a></h3>");
			sb.append("<div>");
			sb.append(getColoredMentionEntitiesOutput(docId, mention));
			sb.append("</div>");
		}
		return sb.toString();

	}

	private String getColoredMentionEntitiesOutput(String docId, String mention) {
		Map<String, List<TracingEntity>> mentionCandidatesOriginalMap = docMentionCandidatesOriginalMap
				.get(docId);
		Map<String, List<TracingEntity>> mentionCandidatesCleanedMap = docMentionCandidatesCleanedMap
				.get(docId);
		Map<String, List<TracingEntity>> mentionCandidatesFinalMap = docMentionCandidatesFinalMap
				.get(docId);
		List<TracingEntity> removedEntities = docRemovedEntities.get(docId);
		StringBuilder sb = new StringBuilder();
		List<TracingEntity> allCandidites = mentionCandidatesOriginalMap
				.get(mention);
		List<TracingEntity> remainingCandiditesAfterDist = mentionCandidatesCleanedMap
				.get(mention);
		List<TracingEntity> remainingCandiditesInFinalGraph = mentionCandidatesFinalMap
				.get(mention);
		Collections.sort(allCandidites);
		Map<String, EntityMetaData> entitiesMetaData = loadEntitiesMetaData(allCandidites);
		sb.append("<table>");
		sb.append("<tr>");
		sb.append("<th></th><th>Candidate Entity</th><th>ME Similarity</th><th>Weighted Degree</th><th>Weighted Degree when removed/final</th><th>Connected Entities</th>");
		sb.append("</tr>");
		for (TracingEntity te : allCandidites) {
			String color;
			double finalDegree = -1;
			if (remainingCandiditesAfterDist != null
					&& !remainingCandiditesAfterDist.contains(te)) {
				color = "#FF7777";
				// degree same as initial
				finalDegree = te.weightedDegree;
			} else if (remainingCandiditesInFinalGraph != null
					&& remainingCandiditesInFinalGraph.contains(te)) {
				color = "#66FF66";
				finalDegree = remainingCandiditesInFinalGraph
						.get(remainingCandiditesInFinalGraph.indexOf(te)).weightedDegree;
			} else { // it was dropped during algorithm iterations
				color = "#000000";
				if(removedEntities != null) {
					int droppingIteration = removedEntities.indexOf(te);
					double orangeDegree = droppingIteration
							/ (double) removedEntities.size();
					color = calculateOrangeDegreeHTMLColor(orangeDegree);

					if (droppingIteration != -1) {
						finalDegree = removedEntities.get(droppingIteration).weightedDegree;
					}
				}
			}
			int connectedEntitiesCount = te.connectedEntities.size();
			
			StringBuilder entitiesInfo = new StringBuilder();
			
			Map<String, Double> connectedEntities = CollectionUtils.sortMapByValue(te.connectedEntities, true);
			for (Entry<String, Double> e : connectedEntities.entrySet()) {
			  entitiesInfo.append(e.getKey() + " ("+ e.getValue() +") --- ");
			}
			
			String entities = entitiesInfo.toString();
			String entryId = (mention + "-" + te.entity).replace("\\", "");

			String uriString = "NO_METADATA";
      String displayString = te.entity;
      if (entitiesMetaData != null && entitiesMetaData.size() > 0) {
        EntityMetaData md = entitiesMetaData.get(te.entity);
        if (md != null) {
          uriString = entitiesMetaData.get(te.entity).getUrl();//Char.encodeURIPathComponent(entityMetaData.getUrl());
          displayString = Char.toHTML(entitiesMetaData.get(te.entity).getHumanReadableRepresentation());
        }
      }
            
			String entityAnchor = "<a target='_blank' href='" + uriString + "'>" + displayString + "</a>";
			sb.append("<tr style ='background-color:"
					+ color
					+ "'> "
					+ "<td>"
					+ "<a target='_blank' href='entity.jsp?entity=" + Char.encodeURIPathComponent(te.entity)+ "'>Info</a>"
					+ "</td>"
					+ "<td>"
					+ entityAnchor
					+ "</td>"
					+ "<td>"
					+ te.MESimilairty
					+ "</td>"
					+ "<td>"
					+ te.weightedDegree
					+ "</td>"
					+ "<td>"
					+ finalDegree
					+ "</td>"
					+ "<td>"
					+ ((connectedEntitiesCount > 0) ? connectedEntitiesCount
					    + "<input id='" + entryId
							+ "-checkbox' type='checkbox' onclick=\"showHide('"
							+ entryId + "');\"/> " + "<label for='" + entryId
							+ "-checkbox' > Show </label>" + "<div id='"
							+ entryId + "-div' style='display:none'>"
							+ entities + "</div>" : "--NA--") + "</td></tr>\n");
		}
		sb.append("</table>");
		return sb.toString();
	}

	private Map<String, EntityMetaData> loadEntitiesMetaData(List<TracingEntity> allCandidites) {
	  
    Set<String> entities = new HashSet<String>();
    for(TracingEntity candidate: allCandidites) {
        entities.add(candidate.entity);
    }

    return DataAccess.getEntitiesMetaData(entities);
  }

  private void addCandidateToMentionsMap(
			Map<String, List<TracingEntity>> mentionEntitiesMap,
			String mention, String candidateEntity,
			double entityWeightedDegree, double MESimilairty,
			Map<String, Double> connectedEntities) {
		TracingEntity te = new TracingEntity(candidateEntity,
				entityWeightedDegree, MESimilairty, connectedEntities);
		List<TracingEntity> mentionCandidates = mentionEntitiesMap.get(mention);
		if (mentionCandidates == null) {
			mentionCandidates = new ArrayList<TracingEntity>();
			mentionEntitiesMap.put(mention, mentionCandidates);
		}
		mentionCandidates.add(te);

	}

	private String calculateOrangeDegreeHTMLColor(double degree) {
		int depth = (int) ((255 - 128) * degree + 128);
		String hex = Integer.toHexString(depth);
		if (hex.length() == 1) {
			hex = 0 + hex;
		}
		String color = "#FF" + hex + "00";
		return color;
	}

  public double getRemovedEntityDegree(String docId, String entityName) {
    TracingEntity te = new TracingEntity(entityName, -1000, null);
    int droppingIteration = docRemovedEntities.get(docId).indexOf(te);
   // assert droppingIteration != -1 : "Still buggy.";
    if (droppingIteration != -1) {
      return docRemovedEntities.get(docId).get(droppingIteration).weightedDegree;
    } else {
      return -2000;
    }
  }
}

class TracingEntity implements Comparable<TracingEntity> {

	String entity;

	double weightedDegree;

	List<String> connectedMentions;

	Map<String, Double> connectedEntities;

	double MESimilairty;// used only within graph

	public TracingEntity(String entity, double weightedDegree,
			List<String> connectedMentions) {
		super();
		this.entity = entity;
		this.weightedDegree = weightedDegree;
		this.connectedMentions = connectedMentions;
	}

	public TracingEntity(String entity, double weightedDegree,
			double MESimilarity, Map<String, Double> connectedEntities) {
		super();
		this.entity = entity;
		this.weightedDegree = weightedDegree;
		this.MESimilairty = MESimilarity;
		this.connectedEntities = connectedEntities;
	}

	@Override
	public int compareTo(TracingEntity o) {
		if (weightedDegree < o.weightedDegree)
			return 1;
		else if (weightedDegree > o.weightedDegree)
			return -1;
		else
			return 0;
	}

	@Override
	public boolean equals(Object obj) {
		return entity.equals(((TracingEntity) obj).entity);
	}

	@Override
	public int hashCode() {
		return entity.hashCode();
	}
	
	public String toString() {
	  return entity;
	}
}
