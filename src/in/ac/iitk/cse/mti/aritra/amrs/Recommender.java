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
import java.sql.DriverManager;
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

import redis.clients.jedis.Jedis;

/**
 * @author aritra
 * 
 */
public class Recommender {
	private final String msdHome;
	private final String dataLocation;
	private final String userHistoryLocation;
	private final String userTagsLocation;
	private final String usersFileLocation;

	private final Map<String, Double> userRecommendations;
	private final MillionSongDataset msdCache;
	private final Jedis[] dbServers;
	private Connection conn;

	private final static int kUsers = 50;
	private final static int recentTrackLength = 100;
	private final static int moodLength = 10;
	private final static int threadCount = 8;
	private final static int recommendationCount = 5;
	private final static int testTracksCount = 2;

	private final PrintStream originalStream;
	private final PrintStream dummyStream;

	private final Logger logger;

	/**
	 * @param dataLocation
	 * @param msdCache
	 */
	public Recommender(String dataLocation, MillionSongDataset msdCache) {
		usersFileLocation = dataLocation + File.separatorChar + "users";
		this.dataLocation = dataLocation + File.separatorChar + "amrs";
		msdHome = this.dataLocation + File.separatorChar + "MillionSong";
		userHistoryLocation = this.dataLocation + File.separatorChar
				+ "userhistory";
		userTagsLocation = this.dataLocation + File.separatorChar + "usertags";
		this.msdCache = msdCache;

		dbServers = new Jedis[Recommender.threadCount];
		for (int i = 0; i < Recommender.threadCount; i++) {
			Jedis dbServer = new Jedis("127.0.0.1");
			dbServer.connect();
			dbServers[i] = dbServer;
		}

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

		userRecommendations = new HashMap<String, Double>();

		originalStream = System.out;
		dummyStream = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) {
			}
		});
		System.setOut(dummyStream);
		logger = Logger.getLogger(this.getClass().getName());
	}

	/**
	 * @param costs
	 */
	private void printCostmatrix(double[][] costs) {
		for (double[] row : costs) {
			for (double element : row) {
				print("" + element);
			}
			print("");
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
	private ArrayList<String> getAllUsers() {
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
						break;
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
	 * @param userTags
	 * @param similarUserTags
	 * @return
	 */
	private double findUserSimilarity(ArrayList<Double> userTags,
			ArrayList<Double> similarUserTags) {
		if (userTags.size() != similarUserTags.size()) {
			System.err.println("WARNING: Normalized tag size does not match");
		}
		double similarity = 0;
		for (int i = 0; i < userTags.size(); i++) {
			similarity += Math.min(userTags.get(i), similarUserTags.get(i))
					- Math.abs(userTags.get(i) - similarUserTags.get(i));
		}
		return similarity;
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
		Map<String, Double> userSimilarities = new HashMap<String, Double>();
		ArrayList<String> similarUsers = new ArrayList<String>();

		ArrayList<Double> userTags = getNormalizedTags(user);

		// otherwise return empty list
		if (userTags.size() > 0) {
			for (String otherUser : users) {
				if (!otherUser.equalsIgnoreCase(user)) {
					ArrayList<Double> similarUserTags = getNormalizedTags(otherUser);
					// otherwise skip user
					if (similarUserTags.size() > 0) {
						double userSimilarity = findUserSimilarity(userTags,
								similarUserTags);
						userSimilarities.put(otherUser, userSimilarity);
					}
				}
			}

			// Sorting in decreasing order of similarity
			Set<Entry<String, Double>> set = userSimilarities.entrySet();
			List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(
					set);
			Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
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
			List<String> trackIds2, int threadId) {
		int dimension = trackIds1.size();
		double[][] costs = new double[dimension][dimension];
		for (int i = 0; i < trackIds1.size(); i++) {
			for (int j = 0; j < trackIds2.size(); j++) {
				costs[i][j] = getTrackSimilarity(trackIds1.get(i),
						trackIds2.get(j), threadId) + 2;
			}
		}
		double similarity = (getCost(costs) - 2 * dimension) / dimension;
		return similarity;
	}

	/**
	 * @param trackIds
	 * @param user
	 * @param threadId
	 * @return
	 */
	private Map<String, Double> getUserRecommendation(
			ArrayList<String> trackIds, String user, int threadId) {
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
		if (trackCount > Recommender.moodLength + 1) {
			for (int offset = 1; offset < Math.min(
					Recommender.recentTrackLength, trackCount)
					- Recommender.moodLength - 1; offset++) {
				List<String> compareWith = similarUserTrackIds.subList(offset,
						offset + Recommender.moodLength);
				double score = getHistorySimilarity(trackIds, compareWith,
						threadId);
				try {
					recommendation.put(similarUserTrackIds.get(offset - 1),
							score);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			System.err.println("Not enough history, skipping user: " + user);
		}

		return recommendation;
	}

	/**
	 * @param user
	 */
	public void recommend(String user) {
		print("Recommendations for User: " + user);
		long start = System.currentTimeMillis();

		ArrayList<String> similarUsers = findSimilarUsers(user,
				Recommender.kUsers);

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
					&& trackCount < Recommender.moodLength
							+ Recommender.testTracksCount) {
				String trackId = line.split(":")[1];
				if (trackCount < Recommender.testTracksCount) {
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
		if (trackCount == Recommender.moodLength) {
			ArrayList<Thread> threads = new ArrayList<Thread>();
			for (int i = 0; i < Recommender.threadCount; i++) {
				Thread t = new CompareUsers(userTrackIds, similarUsers, i);
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

			testRecommendations(userTestTrackIds);
		} else {
			System.err.println("Not enough history present.");
		}
	}

	/**
	 * @param userTestTrackIds
	 */
	private void testRecommendations(ArrayList<String> userTestTrackIds) {
		String toPrint = "Testing Recommendations" + "\n";
		for (String userTrackid : userTestTrackIds) {
			String testTitle = (String) msdCache.getTrackFeatures(userTrackid,
					dbServers[0]).get("title");
			toPrint += "\n" + testTitle + "\n";
			for (Entry<String, Double> recommendedTrackId : getSortedRecommendations(Recommender.recommendationCount)) {
				double similarity = getTrackSimilarity(userTrackid,
						recommendedTrackId.getKey(), 0);
				String trackId = recommendedTrackId.getKey();
				String recommendationTitle = (String) msdCache
						.getTrackFeatures(trackId, dbServers[0]).get("title");
				String temp = similarity + " : " + recommendationTitle;
				toPrint += temp + "\n";
			}
		}
		print(toPrint);
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
	private double cosineSimilarity(ArrayList<Integer> vector1,
			ArrayList<Integer> vector2) {
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
		ArrayList<Integer> artist1TagPool = new ArrayList<Integer>();
		ArrayList<Integer> artist2TagPool = new ArrayList<Integer>();
		for (String rawTag : getArtistTags(artistId1)) {
			artist1TagPool.add(Integer.parseInt(rawTag));
		}
		for (String rawTag : getArtistTags(artistId2)) {
			artist2TagPool.add(Integer.parseInt(rawTag));
		}
		return cosineSimilarity(artist1TagPool, artist2TagPool);
	}

	/**
	 * @param value1
	 * @param value2
	 * @return
	 */
	private double computeDistance(double value1, double value2) {
		if (value1 == 0 || value2 == 0) {
			return 0.0;
		}
		return 1 - Math.abs((value1 - value2) / (value1 + value2));
	}

	/**
	 * @param trackId1
	 * @param trackId2
	 * @param threadId
	 * @return
	 */
	private double getTrackSimilarity(String trackId1, String trackId2,
			int threadId) {
		double[] weight = { 0.25, 0.25, 0.25, 0.25 };
		double[] distances = new double[weight.length];

		Map<String, Object> trackFeatures1 = msdCache.getTrackFeatures(
				trackId1, dbServers[threadId]);
		Map<String, Object> trackFeatures2 = msdCache.getTrackFeatures(
				trackId2, dbServers[threadId]);

		distances[0] = getArtistSimilarity(
				(String) trackFeatures1.get("artist_id"),
				(String) trackFeatures2.get("artist_id"));
		distances[1] = computeDistance((Double) trackFeatures1.get("loudness"),
				(Double) trackFeatures2.get("loudness"));
		distances[2] = (Double) trackFeatures2.get("song_hotttnesss");
		distances[3] = computeDistance((Double) trackFeatures1.get("tempo"),
				(Double) trackFeatures2.get("tempo"));

		double distance = 0;
		for (int i = 0; i < distances.length; i++) {
			distance += distances[i] * weight[i];
		}
		return 1 - distance;
	}

	/**
	 * @param limit
	 * @return
	 */
	private List<Entry<String, Double>> getSortedRecommendations(int limit) {
		Set<Entry<String, Double>> set = userRecommendations.entrySet();
		List<Entry<String, Double>> list = new ArrayList<Entry<String, Double>>(
				set);
		Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1,
					Map.Entry<String, Double> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});
		if (limit >= 0) {
			return list.subList(0, limit);
		}
		return list;
	}

	/**
	 * 
	 */
	private void printRecommendations() {
		String recommendations = "";
		for (Map.Entry<String, Double> entry : getSortedRecommendations(Recommender.recommendationCount)) {
			String trackId = entry.getKey();
			String title = (String) msdCache.getTrackFeatures(trackId,
					dbServers[0]).get("title");
			double score = entry.getValue();
			recommendations += score + " : " + title + "\n";
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

	/**
	 * @param text
	 */
	private void print(String text) {
		logger.log(Level.INFO, text);
	}

	/**
	 * @author aritra
	 * 
	 */
	private class CompareUsers extends Thread {
		private final int index;
		private final ArrayList<String> currentUserTrackIds;
		private final ArrayList<String> similarUsers;

		/**
		 * @param currentUserTrackIds
		 * @param similarUsers
		 * @param index
		 */
		public CompareUsers(ArrayList<String> currentUserTrackIds,
				ArrayList<String> similarUsers, int index) {
			this.currentUserTrackIds = currentUserTrackIds;
			this.similarUsers = similarUsers;
			this.index = index;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			for (int i = 0; i < similarUsers.size(); i++) {
				if (i % Recommender.threadCount == index) {
					String similarUser = similarUsers.get(i);
					Map<String, Double> recommendation = getUserRecommendation(
							currentUserTrackIds, similarUser, index);
					updateRecommendations(recommendation);
				}
			}
		}
	}
}
