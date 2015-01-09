/**
 * 
 */
package in.ac.iitk.cse.mti.aritra.amrs.endpoints;

import in.ac.iitk.cse.mti.aritra.amrs.Recommender;
import in.ac.iitk.cse.mti.aritra.amrs.utils.MillionSongDataset;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import redis.clients.jedis.Jedis;

/**
 * @author aritra
 * 
 */
@Path("/recommend")
public class Recommend {
    
    private Recommender recommender;
    private MillionSongDataset msdCache;
    private Connection conn;
    
    public Recommend() {
        String dataLocation = File.separatorChar + "home" + File.separatorChar
                + "aritra" + File.separatorChar + "Development"
                + File.separatorChar + "data";
        
        msdCache = new MillionSongDataset(dataLocation + File.separatorChar
                + "amrs" + File.separatorChar + "MillionSong");
        
        String msdHome = dataLocation + File.separatorChar + "amrs"
                + File.separatorChar + "MillionSong";
        
        try {
            Class.forName("org.sqlite.JDBC");
            String dbLocation = msdHome + File.separatorChar
                    + "AdditionalFiles" + File.separatorChar + "a_tag_pool.db";
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
            conn.setAutoCommit(false);
            System.out.println("Opened database successfully");
        } catch (Exception e) {
            conn = null;
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
        
        recommender = new Recommender(dataLocation, msdCache, conn, 16);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    public void finalize() throws Throwable {
        conn.close();
        super.finalize();
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Entry<String, Double>> recommend(
            @QueryParam("user") String user, @QueryParam("k") Integer kUsers,
            @QueryParam("mood-length") Integer moodLength,
            @QueryParam("artist-wtg") Integer artistWTG,
            @QueryParam("loudness-wtg") Integer loudnessWTG,
            @QueryParam("tempo-wtg") Integer tempoWTG,
            @QueryParam("similarity-wtg") Integer similarityWTG,
            @QueryParam("popularity-wtg") Integer popularityWTG)
                    throws ServletException {
        if (kUsers == null) {
            kUsers = 50;
        }
        if (moodLength == null) {
            moodLength = 5;
        }
        return recommender.recommend(user, kUsers, moodLength, 10, artistWTG,
                loudnessWTG, tempoWTG, similarityWTG, popularityWTG);
    }
    
    @GET
    @Path("track-info")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getTrackInfo(@QueryParam("id") String trackId)
            throws ServletException {
        System.out.println("Track ID: " + trackId);
        Map<String, Object> result;
        Jedis dbServer = null;
        try {
            dbServer = new Jedis("127.0.0.1");
            dbServer.connect();
            result = msdCache.getTrackFeatures(trackId, dbServer);
            dbServer.close();
        } catch (Exception e) {
            throw new ServletException("Invalid Track ID");
        } finally {
            dbServer.close();
        }
        return result;
    }
    
    @GET
    @Path("users")
    @Produces(MediaType.APPLICATION_JSON)
    public ArrayList<String> getAllUsers(
            @QueryParam("suggestion") String suggestion) {
        suggestion = suggestion == null ? "" : suggestion.toLowerCase();
        ArrayList<String> result = new ArrayList<String>();
        for (String user : recommender.getAllUsers()) {
            if (user.toLowerCase().contains(suggestion)) {
                result.add(user);
            }
        }
        return result;
    }
}
