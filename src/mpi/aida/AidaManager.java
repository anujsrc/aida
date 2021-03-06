package mpi.aida;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import mpi.aida.access.DataAccess;
import mpi.aida.data.Entities;
import mpi.aida.data.Entity;
import mpi.aida.data.Mention;
import mpi.aida.data.Mentions;
import mpi.aida.data.ResultEntity;
import mpi.aida.data.Type;
import mpi.aida.util.ClassPathUtils;
import mpi.aida.util.YagoUtil.Gender;
import mpi.tokenizer.data.Tokenizer;
import mpi.tokenizer.data.TokenizerManager;
import mpi.tokenizer.data.Tokens;
import mpi.tools.basics.Normalize;
import mpi.tools.database.DBConnection;
import mpi.tools.database.DBSettings;
import mpi.tools.database.MultipleDBManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AidaManager {

  static {
    // Always need to do this.
    init();
  }
  
  private static Logger slogger_ = LoggerFactory.getLogger(AidaManager.class);

  // This is more couple to SQL than it should be. Works for now.
  public static final String DB_AIDA = "DatabaseAida";
  
  public static final String DB_YAGO2 = "DatabaseYago";

  public static final String DB_YAGO2_FULL = "DatabaseYago2Full";

  public static final String DB_YAGO2_SPOTLX = "DatabaseYago2SPOTLX";

  public static final String DB_RMI_LOGGER = "DatabaseRMILogger";

  public static final String DB_HYENA = "DatabaseHYENA";

  public static String databaseAidaConfig = "database_aida.properties";
  
  private static String databaseYago2Config = "database_yago2.properties";

  private static String databaseYAGO2SPOTLXConfig = "database_yago2spotlx.properties";

  private static String databaseRMILoggerConfig = "databaseRmiLogger.properties";

  private static String databaseHYENAConfig = "database_hyena.properties";

  public static final String WIKIPEDIA_PREFIX = "http://en.wikipedia.org/wiki/";

  public static final String YAGO_PREFIX = "http://yago-knowledge.org/resource/";

  private static Map<String, String> dbIdToConfig = new HashMap<String, String>();

  static {
    dbIdToConfig.put(DB_AIDA, databaseAidaConfig);
    dbIdToConfig.put(DB_YAGO2, databaseYago2Config);
    dbIdToConfig.put(DB_YAGO2_SPOTLX, databaseYAGO2SPOTLXConfig);
    dbIdToConfig.put(DB_RMI_LOGGER, databaseRMILoggerConfig);
    dbIdToConfig.put(DB_HYENA, databaseHYENAConfig);
  }

  private static AidaManager tasks = null;

  public static enum language {
    english, german
  }

  private static final Set<String> malePronouns = new HashSet<String>() {

    private static final long serialVersionUID = 2L;
    {
      add("He");
      add("he");
      add("Him");
      add("him");
      add("His");
      add("his");
    }
  };

  private static final Set<String> femalePronouns = new HashSet<String>() {

    private static final long serialVersionUID = 3L;
    {
      add("she");
      add("she");
      add("Her");
      add("her");
      add("Hers");
      add("hers");
    }
  };

  public static void init() {
    getTasksInstance();
  }

  private static synchronized AidaManager getTasksInstance() {
    if (tasks == null) {
      tasks = new AidaManager();
    }
    return tasks;
  }

 

  /**
   * tokenizes only the text,
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenize(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.tokens, lemmatize);
  }

  public static Tokens tokenize(String text) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.tokens, false);
  }

  /**
   * tokenizes the text with POS and NER
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizeNER( String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.ner, lemmatize);
  }

  /**
   * tokenizes the text with POS
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePOS(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.pos, lemmatize);
  }

  /**
   * tokenizes the text with PARSE
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePARSE(String text, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, Tokenizer.type.parse, lemmatize);
  }

  /**
   * tokenizes the text with PARSE
   * 
   * @param docId
   * @param text
   * @return
   */
  public static Tokens tokenizePARSE(String text, Tokenizer.type type, boolean lemmatize) {
    return AidaManager.getTasksInstance().tokenize(text, type, lemmatize);
  }

  public static synchronized DBConnection getConnectionForDatabase(String dbId, String req) throws SQLException {
    if (!MultipleDBManager.isConnected(dbId)) {
      try {
        Properties prop = ClassPathUtils.getPropertiesFromClasspath(dbIdToConfig.get(dbId));
        String type = prop.getProperty("type");
        String service = null;
        if (type.equalsIgnoreCase("Oracle")) {
          service = prop.getProperty("serviceName");
        } else if (type.equalsIgnoreCase("PostGres")) {
          service = prop.getProperty("schema");
        }
        String hostname = prop.getProperty("hostname");
        Integer port = Integer.parseInt(prop.getProperty("port"));
        String username = prop.getProperty("username");
        String password = prop.getProperty("password");
        Integer maxCon = Integer.parseInt(prop.getProperty("maxConnection"));

        DBSettings settings = new DBSettings(hostname, port, username, password, maxCon, type, service);
        MultipleDBManager.addDatabase(dbId, settings);
        slogger_.info("Connecting to " + type + " database " + username + "@" + hostname + ":" + port + "/" + service);
      } catch (Exception e) {
        slogger_.error("Error connecting to the AIDA database: " + e.getLocalizedMessage());
      }
    }
    if (!MultipleDBManager.isConnected(dbId)) {
      slogger_.error("Could not connect to the AIDA database. " + "Please check the settings in 'settings/database_aida.properties'"
          + "and make sure the Postgres server is up and running.");
      return null;
    }
    return MultipleDBManager.getConnection(dbId, req);
  }

  public static void releaseConnection(String dbId, DBConnection con) {
    MultipleDBManager.releaseConnection(dbId, con);
  }

  /**
   * Gets an AIDA entity for the given YAGO entity id.
   * This is slow, as it accesses the DB for each call.
   * Do in batch using DataAccess directly for a larger number 
   * of entities.
   * 
   * @param yagoEntityId  ID in YAGO2 format
   * @return              AIDA Entity
   */
  public static Entity getEntity(String yagoEntityId) {
    return new Entity(yagoEntityId, DataAccess.getIdForYagoEntityId(yagoEntityId));
  }

  /**
   * Gets an AIDA entity for the given AIDA entity id.
   * This is slow, as it accesses the DB for each call.
   * Do in batch using DataAccess directly for a larger number 
   * of entities.
   * 
   * @param entityId  Internal AIDA int ID 
   * @return          AIDA Entity
   */
  public static Entity getEntity(int entityId) {
    return new Entity(DataAccess.getYagoEntityIdForId(entityId), entityId);
  }

  /**
   * Creates the Wikipedia link for a given ResultEntity.
   * 
   * @param resultEntity  Given AIDA ResultEntity.
   * @return  Wikipedia Link for the entity.
   */
  public static String getWikipediaUrl(ResultEntity resultEntity) {
    Entity entity = getEntity(resultEntity.getEntity());
    String titlePart = Normalize.unEntity(entity.getName()).replace(' ', '_');
    return WIKIPEDIA_PREFIX + titlePart;
  }

  /**
   * Creates the YAGO identifier for a given ResultEntity.
   * 
   * @param resultEntity  Given AIDA ResultEntity.
   * @return  YAGO Resource for the entity.
   */
  public static String getYAGOIdentifier(ResultEntity resultEntity) {
    Entity entity = getEntity(resultEntity.getEntity());
    String titlePart = Normalize.unEntity(entity.getName()).replace(' ', '_');
    return YAGO_PREFIX + titlePart;
  }

  /**
   * Creates the Wikipedia link for a given entity.
   * 
   * @param entity  Given AIDA entity.
   * @return  Wikipedia Link for the entity.
   */
  public static String getWikipediaUrl(Entity entity) {
    String titlePart = Normalize.unEntity(entity.getName()).replace(' ', '_');
    return WIKIPEDIA_PREFIX + titlePart;
  }

  /**
   * Creates the YAGO identifier for a given entity.
   * 
   * @param entity  Given AIDA entity.
   * @return  YAGO Resource for the entity.
   */
  public static String getYAGOIdentifier(Entity entity) {
    String titlePart = Normalize.unEntity(entity.getName()).replace(' ', '_');
    return YAGO_PREFIX + titlePart;
  }

  /**
   * Returns the potential entity candidates for a mention (via the YAGO
   * 'means' relation)
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @return Candidate entities for this mention.
   * 
   */
  public static Entities getEntitiesForMention(String mention) {
    return DataAccess.getEntitiesForMention(mention, 1.0);
  }

  /**
   * Returns the potential entity candidates for a mention (via the YAGO
   * 'means' relation)
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @param maxEntityRank Retrieve entities up to a global rank, where rank is 
   * between 0.0 (best) and 1.0 (worst). Setting to 1.0 will retrieve all entities.
   * @return Candidate entities for this mention.
   * 
   */
  public static Entities getEntitiesForMention(Mention mention, double maxEntityRank) {
    return DataAccess.getEntitiesForMention(mention.getMention(), maxEntityRank);
  }

  /**
   * Returns the potential entity candidates for a mention (via the YAGO
   * 'means' relation) and filters those candidates against the given list of
   * types
   * 
   * @param mention
   *            Mention to get entity candidates for
   * @return Candidate entities for this mention (in YAGO2 encoding) including
   *         their prior probability
   * @throws SQLException
   */
  public static Entities getEntitiesForMention(Mention mention, Set<Type> filteringTypes, double maxEntityRank) throws SQLException {
    Entities entities = getEntitiesForMention(mention, maxEntityRank);
    Entities filteredEntities = new Entities();
    Set<String> entityNames = entities.getUniqueNames();
    Map<String, Set<Type>> entitiesTypes = DataAccess.getTypes(entityNames);
    for (Entry<String, Set<Type>> entry : entitiesTypes.entrySet()) {
      String entityName = entry.getKey();
      Set<Type> entityTypes = entry.getValue();
      for (Type t : entityTypes) {
        if (filteringTypes.contains(t)) {
          filteredEntities.add(new Entity(entityName, entities
              .getId(entityName)));
          break;
        }
      }
    }
    return filteredEntities;
  }

  public static Map<String, Gender> getGenderForEntities(Entities entities) {
    return DataAccess.getGenderForEntities(entities);
  }

  public static void fillInCandidateEntities(Mentions mentions) throws SQLException {
    fillInCandidateEntities(mentions, false, false, 1.0);
  }

  /**
   * Retrieves all the candidate entities for the given mentions.
   * 
   * @param mentions  All mentions in the input doc.
   * @param includeNullEntityCandidates Set to true to include mentions flagged
   * as NME in the ground-truth data.
   * @param includeContextMentions  Include mentions as context.
   * @param maxEntityRank Fraction of entities to include. Between 0.0 (none)
   * and 1.0 (all). The ranks are taken from the entity_rank table.
   * @throws SQLException
   */
  public static void fillInCandidateEntities(Mentions mentions, boolean includeNullEntityCandidates, boolean includeContextMentions,
      double maxEntityRank) throws SQLException {    
    Set<Type> filteringTypes = mentions.getEntitiesTypes();
    for (int i = 0; i < mentions.getMentions().size(); i++) {
      Mention m = mentions.getMentions().get(i);
      Entities mentionCandidateEntities;
      if (malePronouns.contains(m.getMention()) || femalePronouns.contains(m.getMention())) {
        setCandiatesFromPreviousMentions(mentions, i);
      } else {

        if (filteringTypes != null) {
          mentionCandidateEntities = AidaManager.getEntitiesForMention(m, filteringTypes, maxEntityRank);
        } else {
          mentionCandidateEntities = AidaManager.getEntitiesForMention(m, maxEntityRank);
        }

        if (includeNullEntityCandidates) {
          Entity nmeEntity = new Entity(Entities.getMentionNMEKey(m.getMention()), -1);

          // add surrounding mentions as context
          if (includeContextMentions) {
            List<String> surroundingMentionsNames = new LinkedList<String>();
            int begin = Math.max(i - 2, 0);
            int end = Math.min(i + 3, mentions.getMentions().size());

            for (int s = begin; s < end; s++) {
              if (s == i) continue; // skip mention itself
              surroundingMentionsNames.add(mentions.getMentions().get(s).getMention());
            }
            nmeEntity.setSurroundingMentionNames(surroundingMentionsNames);
          }

          mentionCandidateEntities.add(nmeEntity);
        }
        m.setCandidateEntities(mentionCandidateEntities);
      }
    }
  }

  private static void setCandiatesFromPreviousMentions(Mentions mentions, int mentionIndex) {
    Mention mention = mentions.getMentions().get(mentionIndex);
    Entities allPrevCandidates = new Entities();
    if (mentionIndex == 0) {
      mention.setCandidateEntities(allPrevCandidates);
      return;
    }

    for (int i = 0; i < mentionIndex; i++) {
      Mention m = mentions.getMentions().get(i);
      for (Entity e : m.getCandidateEntities()) {
        allPrevCandidates.add(new Entity(e.getName(), e.getId()));
      }
    }

    Map<String, Gender> entitiesGenders = AidaManager.getGenderForEntities(allPrevCandidates);

    Gender targetGender = null;
    if (malePronouns.contains(mention.getMention())) targetGender = Gender.MALE;
    else if (femalePronouns.contains(mention.getMention())) targetGender = Gender.FEMALE;

    Entities filteredCandidates = new Entities();
    for (Entity e : allPrevCandidates) {
      if (entitiesGenders != null && entitiesGenders.containsKey(e.getName()) && entitiesGenders.get(e.getName()) == targetGender) filteredCandidates
          .add(e);
    }
    mention.setCandidateEntities(filteredCandidates);
  }

  public static boolean isNamedEntity(String entity) {
    return AidaManager.getTasksInstance().checkIsNamedEntity(entity);
  }

  public static void main(String[] args) throws SQLException {
    Entities entities = getEntitiesForMention("Germany");
    for (Entity entity : entities) {
      System.out.println(entity.getName());
    }
  }

  private AidaManager() {
    TokenizerManager.init();
  }

  private Tokens tokenize(String text, Tokenizer.type type, boolean lemmatize) {
    return TokenizerManager.tokenize(text, type, lemmatize);
  }

  private boolean checkIsNamedEntity(String entity) {
    if (Normalize.unWordNetEntity(entity) == null && Normalize.unWikiCategory(entity) == null && Normalize.unGeonamesClass(entity) == null
        && Normalize.unGeonamesEntity(entity) == null) {
      return true;
    } else {
      return false;
    }
  }
}
