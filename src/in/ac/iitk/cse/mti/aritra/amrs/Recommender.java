/**
 * 
 */
package in.ac.iitk.cse.mti.aritra.amrs;

import in.ac.iitk.cse.mti.aritra.amrs.utils.MillionSongDataset;
import in.ac.iitk.cse.mti.aritra.amrs.vendor.HungarianAlgorithmEdu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import redis.clients.jedis.Jedis;

/**
 * @author aritra
 * 
 */
public class Recommender {
	private final String dataLocation;
	private final String userHistoryLocation;
	private final String userTagsLocation;
	private final String usersFileLocation;

	private final Map<String, Double> userRecommendations;
	private final Map<String, Double> userSimilarities;
	private final MillionSongDataset msdCache;
	private final Jedis[] dbServers;
	private final Connection conn;

	private int threadCount;

	private final PrintStream originalStream;
	private final PrintStream dummyStream;

	private final Logger logger;

	public Recommender(String dataLocation, MillionSongDataset msdCache,
			Connection conn) {
		this(dataLocation, msdCache, conn, 16);
	}

	/**
	 * @param dataLocation
	 * @param msdCache
	 */
	public Recommender(String dataLocation, MillionSongDataset msdCache,
			Connection conn, int threadCount) {
		usersFileLocation = dataLocation + File.separatorChar + "users";
		this.dataLocation = dataLocation + File.separatorChar + "amrs";

		userHistoryLocation = this.dataLocation + File.separatorChar
				+ "userhistory";
		userTagsLocation = this.dataLocation + File.separatorChar + "usertags";

		this.msdCache = msdCache;
		this.threadCount = threadCount;

		dbServers = new Jedis[threadCount];
		for (int i = 0; i < threadCount; i++) {
			dbServers[i] = new Jedis("127.0.0.1");
			dbServers[i].connect();
		}

		this.conn = conn;

		userRecommendations = new HashMap<String, Double>();
		userSimilarities = new HashMap<String, Double>();

		originalStream = System.out;
		dummyStream = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) {
			}
		});
		System.setOut(dummyStream);
		logger = Logger.getLogger(this.getClass().getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		for (Jedis dbServer : dbServers) {
			dbServer.close();
		}
		System.setOut(originalStream);
		super.finalize();
	}

	/**
	 * @param costs
	 */
	private void printCostmatrix(double[][] costs) {
		for (double[] row : costs) {
			for (double element : row) {
				print("" + element);
			}
			println("");
		}
	}

	/**
	 * @param costs
	 * @return
	 */
	private double getCost(double[][] costs) {
		int[][] result = HungarianAlgorithmEdu.hgAlgorithm(costs, "min");

		double cost = 0;
		for (int[] row : result) {
			cost += costs[row[0]][row[1]];
		}
		return cost;
	}

	/**
	 * @return
	 */
	public ArrayList<String> getAllUsers() {
		ArrayList<String> users = new ArrayList<String>();
		File usersFile = new File(usersFileLocation);
		if (!usersFile.exists()) {
			System.err.println("Users file not found");
		} else {
			try {
				BufferedReader br = new BufferedReader(
						new FileReader(usersFile));
				String line = null;
				int count = 0;
				while ((line = br.readLine()) != null) {
					String user = line.replaceAll("\r\n", "");
					users.add(user);
					count++;
					if (count == 422) {
						continue;
					}
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return users;
	}

	/**
	 * @param user
	 * @return
	 */
	private ArrayList<Double> getNormalizedTags(String user) {
		int trackCount = 0;
		try {
			File userHistoryFile = new File(userHistoryLocation
					+ File.separatorChar + user);
			BufferedReader br = new BufferedReader(new FileReader(
					userHistoryFile));
			while (br.readLine() != null) {
				trackCount++;
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		ArrayList<Double> tagCounts = new ArrayList<Double>();
		if (trackCount > 0) {
			try {
				File userTagsFile = new File(userTagsLocation
						+ File.separatorChar + user);
				BufferedReader br = new BufferedReader(new FileReader(
						userTagsFile));
				String line = null;
				while ((line = br.readLine()) != null) {
					tagCounts.add((double) Integer.parseInt(line) / trackCount);
				}
				br.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		ArrayList<Double> normalizedTags = new ArrayList<Double>();
		double sum = 0;
		for (double tagCount : tagCounts) {
			sum += tagCount;
		}
		if (sum > 0) {
			for (double tagCount : tagCounts) {
				normalizedTags.add(tagCount / sum);
			}
		}

		return normalizedTags;
	}

	/**
	 * @param user
	 * @param limit
	 * @return
	 */
	private ArrayList<String> findSimilarUsers(String user, int limit) {
		ArrayList<String> users = getAllUsers();
		ArrayList<String> similarUsers = new ArrayList<String>();

		ArrayList<Double> userTags = getNormalizedTags(user);

		// otherwise return empty list
		if (userTags.size() > 0) {
			ArrayList<Thread> threads = new ArrayList<Thread>();
			for (int i = 0; i < threadCount; i++) {
				Thread t = new SimilarUsers(user, userTags, users, i);
				threads.add(t);
				t.start();
			}
			for (Thread t : threads) {
				try {
					t.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// Sorting in decreasing order of similarity
			Set<Entry<String, Double>> set = userSimilarities.entrySet();
			List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(
					set);
			Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
				@Override
				public int compare(Map.Entry<String, Double> o1,
						Map.Entry<String, Double> o2) {
					return o2.getValue().compareTo(o1.getValue());
				}
			});
			for (Map.Entry<String, Double> entry : list) {
				if (limit <= 0) {
					break;
				}
				similarUsers.add(entry.getKey());
				limit--;
			}
		}

		return similarUsers;
	}

	/**
	 * @param trackIds1
	 * @param trackIds2
	 * @param threadId
	 * @return
	 */
	private double getHistorySimilarity(List<String> trackIds1,
			List<String> trackIds2, int artistWTG, int loudnessWTG,
			int tempoWTG, int threadId) {
		int dimension = trackIds1.size();
		double[][] costs = new double[dimension][dimension];
		for (int i = 0; i < trackIds1.size(); i++) {
			for (int j = 0; j < trackIds2.size(); j++) {
				costs[i][j] = getTrackSimilarity(trackIds1.get(i),
						trackIds2.get(j), artistWTG, loudnessWTG, tempoWTG,
						threadId);
			}
		}
		double similarity = getCost(costs) / dimension;
		return similarity;
	}

	/**
	 * @param trackIds
	 * @param user
	 * @param threadId
	 * @return
	 */
	private Map<String, Double> getUserRecommendation(
			ArrayList<String> trackIds, String user, int moodLength,
			int artistWTG, int loudnessWTG, int tempoWTG, int similarityWTG,
			int popularityWTG, int threadId) {
		Map<String, Double> recommendation = new HashMap<String, Double>();

		ArrayList<String> similarUserTrackIds = new ArrayList<String>();
		try {
			File userHistoryFile = new File(userHistoryLocation
					+ File.separatorChar + user);
			BufferedReader br = new BufferedReader(new FileReader(
					userHistoryFile));
			String line = null;
			while ((line = br.readLine()) != null) {
				String trackId = line.split(":")[1];
				similarUserTrackIds.add(trackId);
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		int trackCount = similarUserTrackIds.size();
		if (trackCount > moodLength + 1) {
			// int limit = Math.min(Recommender.recentTrackLength, trackCount)
			// - (Recommender.moodLength + 1);
			int limit = trackCount - (moodLength + 1);
			for (int offset = 1; offset < limit; offset++) {
				List<String> compareWith = similarUserTrackIds.subList(offset,
						offset + moodLength);

				double similarity = getHistorySimilarity(trackIds, compareWith,
						artistWTG, loudnessWTG, tempoWTG, threadId);
				String nextTrackId = similarUserTrackIds.get(offset - 1);
				double trackPopularity = (Double) msdCache.getTrackFeatures(
						nextTrackId, dbServers[threadId])
						.get("song_hotttnesss");
				double score = similarity * (similarityWTG / 100.0)
						+ trackPopularity * (popularityWTG / 100.0);
				recommendation.put(nextTrackId, score);
			}
		} else {
			System.err.println("Not enough history, skipping user: " + user);
		}

		return recommendation;
	}

	public List<Entry<String, Double>> recommend(String user)
			throws ServletException {
		return recommend(user, 50, 5, 10, 60, 20, 20, 80, 20);
	}

	/**
	 * @param user
	 * @return
	 */
	public List<Entry<String, Double>> recommend(String user, int kUsers,
			int moodLength, int testTracksCount, int artistWTG,
			int loudnessWTG, int tempoWTG, int similarityWTG, int popularityWTG)
			throws ServletException {
		print("Recommendations for User: " + user);
		long start = System.currentTimeMillis();

		ArrayList<String> similarUsers = findSimilarUsers(user, kUsers);

		ArrayList<String> userTestTrackIds = new ArrayList<String>();
		ArrayList<String> userTrackIds = new ArrayList<String>();
		try {
			int trackCount = 0;
			File userHistoryFile = new File(userHistoryLocation
					+ File.separatorChar + user);
			BufferedReader br = new BufferedReader(new FileReader(
					userHistoryFile));
			String line = null;
			while ((line = br.readLine()) != null
					&& trackCount < moodLength + testTracksCount) {
				String trackId = line.split(":")[1];
				if (trackCount < testTracksCount) {
					userTestTrackIds.add(trackId);
				} else {
					userTrackIds.add(trackId);
				}
				trackCount++;
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		int trackCount = userTrackIds.size();
		if (trackCount == moodLength) {
			ArrayList<Thread> threads = new ArrayList<Thread>();
			for (int i = 0; i < threadCount; i++) {
				Thread t = new CompareUsers(userTrackIds, similarUsers,
						moodLength, artistWTG, loudnessWTG, tempoWTG,
						similarityWTG, popularityWTG, i);
				threads.add(t);
				t.start();
			}
			for (Thread t : threads) {
				try {
					t.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			long stop = System.currentTimeMillis();
			printRecommendations();
			long millis = stop - start;
			long second = millis / 1000 % 60;
			long minute = millis / (1000 * 60) % 60;
			long hour = millis / (1000 * 60 * 60) % 24;
			String time = String.format("%02d:%02d:%02d:%d", hour, minute,
					second, millis);
			print("Time taken: " + time);
			print("Test Result: " + testRecommendations(userTestTrackIds));
			return getSortedRecommendations().subList(0, 10);
		} else {
			System.err.println("Not enough history present.");
			throw new ServletException("Not enough history present.");
		}
	}

	/**
	 * @param userTestTrackIds
	 */
	private double testRecommendations(ArrayList<String> userTestTrackIds) {
		double matchIndex = 0;
		for (Entry<String, Double> recommendedTrack : getSortedRecommendations()) {
			matchIndex++;
			String recommendedTrackId = recommendedTrack.getKey();
			if (userTestTrackIds.contains(recommendedTrackId)) {
				return matchIndex;
			}
		}
		return 0;
	}

	/**
	 * @param artistId
	 * @return
	 */
	private String[] getArtistTags(String artistId) {
		String tagPool = "";
		try {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT tag_pool FROM pooled_tags WHERE artist_id='"
							+ artistId + "';");
			while (rs.next()) {
				tagPool = rs.getString("tag_pool");
				break;
			}
			rs.close();
			stmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return tagPool.split(",");
	}

	/**
	 * @param vector1
	 * @param vector2
	 * @return
	 */
	private double cosineSimilarity(ArrayList<Double> vector1,
			ArrayList<Double> vector2) {
		double dotProduct = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < vector1.size(); i++) {
			dotProduct += vector1.get(i) * vector2.get(i);
			normA += Math.pow(vector1.get(i), 2);
			normB += Math.pow(vector2.get(i), 2);
		}
		if (normA == 0 || normB == 0) {
			return 0.0;
		}
		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}

	/**
	 * @param artistId1
	 * @param artistId2
	 * @return
	 */
	private double getArtistSimilarity(String artistId1, String artistId2) {
		ArrayList<Double> artist1TagPool = new ArrayList<Double>();
		ArrayList<Double> artist2TagPool = new ArrayList<Double>();
		for (String rawTag : getArtistTags(artistId1)) {
			artist1TagPool.add(Double.parseDouble(rawTag));
		}
		for (String rawTag : getArtistTags(artistId2)) {
			artist2TagPool.add(Double.parseDouble(rawTag));
		}
		return cosineSimilarity(artist1TagPool, artist2TagPool);
	}

	/**
	 * @param trackId1
	 * @param trackId2
	 * @param threadId
	 * @return
	 */
	private double getTrackSimilarity(String trackId1, String trackId2,
			int artistWTG, int loudnessWTG, int tempoWTG, int threadId) {
		Map<String, Object> trackFeatures1 = msdCache.getTrackFeatures(
				trackId1, dbServers[threadId]);
		Map<String, Object> trackFeatures2 = msdCache.getTrackFeatures(
				trackId2, dbServers[threadId]);

		double similarity = 0.0;
		double tempSim = 0.0;

		// Artist Similarity
		tempSim = getArtistSimilarity((String) trackFeatures1.get("artist_id"),
				(String) trackFeatures2.get("artist_id"));
		similarity += tempSim * (artistWTG / 100.0);

		// Loudness Similarity
		double loudness1 = (Double) trackFeatures1.get("loudness");
		double loudness2 = (Double) trackFeatures2.get("loudness");
		tempSim = 1.0 - (Math.abs(loudness2 - loudness1) - (-100.0)) / 200.0;
		similarity += tempSim * (loudnessWTG / 100.0);

		// Tempo Similarity
		double tempo1 = (Double) trackFeatures1.get("tempo");
		double tempo2 = (Double) trackFeatures2.get("tempo");
		tempSim = 1.0 - (Math.abs(tempo2 - tempo1) - 0.0) / 500.0;
		similarity += tempSim * (tempoWTG / 100.0);

		return 1 - similarity;
	}

	/**
	 * @param limit
	 * @return
	 */
	private List<Entry<String, Double>> getSortedRecommendations() {
		Set<Entry<String, Double>> set = userRecommendations.entrySet();
		List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(
				set);
		Collections.sort(list, new Comparator<Entry<String, Double>>() {
			@Override
			public int compare(Entry<String, Double> o1,
					Entry<String, Double> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});
		return list;
	}

	/**
	 * 
	 */
	private void printRecommendations() {
		String recommendations = "";
		int limit = 10;
		for (Entry<String, Double> entry : getSortedRecommendations()) {
			if (limit <= 0) {
				break;
			}
			String trackId = entry.getKey();
			String title = (String) msdCache.getTrackFeatures(trackId,
					dbServers[0]).get("title");
			double score = entry.getValue();
			recommendations += score + " : " + title + "\n";
			limit--;
		}
		print("Recommendations: " + "\n" + recommendations);
	}

	/**
	 * @param recommendation
	 */
	private synchronized void updateRecommendations(
			Map<String, Double> recommendation) {
		userRecommendations.putAll(recommendation);
	}

	private synchronized void updateSimilarUsers(
			Map<String, Double> userSimilarity) {
		userSimilarities.putAll(userSimilarity);
	}

	private void print(String text) {
		logger.log(Level.INFO, text);
	}

	private void println(String text) {
		print(text + "\n");
	}

	private class CompareUsers extends Thread {
		private final int index;
		private final ArrayList<String> currentUserTrackIds;
		private final ArrayList<String> similarUsers;
		private final int moodLength;
		private final int artistWTG;
		private final int loudnessWTG;
		private final int tempoWTG;
		private final int similarityWTG;
		private final int popularityWTG;

		/**
		 * @param currentUserTrackIds
		 * @param similarUsers
		 * @param index
		 */
		public CompareUsers(ArrayList<String> currentUserTrackIds,
				ArrayList<String> similarUsers, int moodLength, int artistWTG,
				int loudnessWTG, int tempoWTG, int similarityWTG,
				int popularityWTG, int index) {
			this.currentUserTrackIds = currentUserTrackIds;
			this.similarUsers = similarUsers;
			this.index = index;
			this.moodLength = moodLength;
			this.artistWTG = artistWTG;
			this.loudnessWTG = loudnessWTG;
			this.tempoWTG = tempoWTG;
			this.similarityWTG = similarityWTG;
			this.popularityWTG = popularityWTG;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			Map<String, Double> recommendations = new HashMap<String, Double>();
			for (int i = 0; i < similarUsers.size(); i++) {
				if (i % threadCount == index) {
					String similarUser = similarUsers.get(i);
					Map<String, Double> recommendation = getUserRecommendation(
							currentUserTrackIds, similarUser, moodLength,
							artistWTG, loudnessWTG, tempoWTG, similarityWTG,
							popularityWTG, index);
					recommendations.putAll(recommendation);
				}
			}
			updateRecommendations(recommendations);
		}
	}

	private class SimilarUsers extends Thread {
		private final int index;
		private final String user;
		private final ArrayList<Double> userTags;
		private final ArrayList<String> users;

		/**
		 * @param currentUserTrackIds
		 * @param similarUsers
		 * @param index
		 */
		public SimilarUsers(String user, ArrayList<Double> userTags,
				ArrayList<String> users, int index) {
			this.user = user;
			this.userTags = userTags;
			this.users = users;
			this.index = index;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			Map<String, Double> userSimilarities = new HashMap<String, Double>();
			for (int i = 0; i < users.size(); i++) {
				if (i % threadCount == index) {
					String otherUser = users.get(i);
					if (!otherUser.equalsIgnoreCase(user)) {
						ArrayList<Double> userTagsSimilarity = getNormalizedTags(otherUser);
						// otherwise skip user
						if (userTagsSimilarity.size() > 0) {
							double userSimilarity = cosineSimilarity(userTags,
									userTagsSimilarity);
							userSimilarities.put(otherUser, userSimilarity);
						}
					}
				}
			}
			updateSimilarUsers(userSimilarities);
		}
	}
}
