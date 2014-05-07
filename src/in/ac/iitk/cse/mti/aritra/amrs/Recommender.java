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

	private Connection conn;

	private final int kUsers = 200;
	private final int moodLength = 10;
	private final int recentTrackLength = 100;
	private final static int threadCount = 16;

	private final Map<String, Double> userRecommendations;
	private final MillionSongDataset msdCache;

	private final PrintStream originalStream;
	private final PrintStream dummyStream;

	public Recommender(String dataLocation, MillionSongDataset msdCache) {
		usersFileLocation = dataLocation + File.separatorChar + "users";
		this.dataLocation = dataLocation + File.separatorChar + "amrs";
		msdHome = this.dataLocation + File.separatorChar + "MillionSong";
		userHistoryLocation = this.dataLocation + File.separatorChar
				+ "userhistory";
		userTagsLocation = this.dataLocation + File.separatorChar + "usertags";
		this.msdCache = msdCache;

		conn = null;
		try {
			Class.forName("org.sqlite.JDBC");
			String dbLocation = msdHome + File.separatorChar
					+ "AdditionalFiles" + File.separatorChar + "a_tag_pool.db";
			conn = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
			conn.setAutoCommit(false);
			System.out.println("Opened database successfully");
		} catch (Exception e) {
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
	}

	private double getCost(double[][] costs) {
		System.setOut(dummyStream);
		int[][] result = HungarianAlgorithmEdu.hgAlgorithm(costs, "min");
		System.setOut(originalStream);

		double cost = 0;
		for (int[] row : result) {
			cost += costs[row[0]][row[1]];
		}
		return cost;
	}

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
				while ((line = br.readLine()) != null) {
					String user = line.replaceAll("\r\n", "");
					users.add(user);
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return users;
	}

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

	private ArrayList<Double> getNormalizedTags(String user) {
		// Getting the number of tracks to normalize the tag counts
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

	private double getHistorySimilarity(List<String> trackIds1,
			List<String> trackIds2) {
		int dimension = trackIds1.size();
		double[][] costs = new double[dimension][dimension];
		for (int i = 0; i < trackIds1.size(); i++) {
			for (int j = 0; j < trackIds2.size(); j++) {
				costs[i][j] = getTrackSimilarity(trackIds1.get(i),
						trackIds2.get(j)) + 2;
			}
		}
		return (getCost(costs) - 2 * dimension) / dimension;
	}

	private Map<String, Double> getUserRecommendation(
			ArrayList<String> trackIds, String user) {
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
			for (int offset = 1; offset < Math.min(recentTrackLength,
					trackCount) - moodLength - 1; offset++) {
				List<String> compareWith = similarUserTrackIds.subList(offset,
						offset + moodLength);
				double score = getHistorySimilarity(trackIds, compareWith);
				try {
					recommendation.put(
							(String) msdCache.getTrackFeatures(
									similarUserTrackIds.get(offset - 1)).get(
									"title"), score);
					if (offset >= 2) {
						recommendation.put(
								(String) msdCache.getTrackFeatures(
										similarUserTrackIds.get(offset - 2))
										.get("title"), score);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			System.out.println("Not enough history, skipping user: " + user);
		}

		return recommendation;
	}

	public void recommend(String user) {
		// Step 1
		ArrayList<String> similarUsers = findSimilarUsers(user, kUsers);

		ArrayList<String> userTrackIds = new ArrayList<String>();
		try {
			int trackCount = 0;
			File userHistoryFile = new File(userHistoryLocation
					+ File.separatorChar + user);
			BufferedReader br = new BufferedReader(new FileReader(
					userHistoryFile));
			String line = null;
			while ((line = br.readLine()) != null && trackCount < moodLength) {
				String trackId = line.split(":")[1];
				userTrackIds.add(trackId);
				trackCount++;
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		int trackCount = userTrackIds.size();
		if (trackCount == moodLength) {
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
			printRecommendations();
		} else {
			System.out.println("Not enough history present.");
		}
	}

	private String[] getArtistTags(String artistId) {
		String tagPool = "";
		try {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT tag_pool FROM pooled_tags WHERE artist_id="
							+ artistId + ";");
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
		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}

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

	private double computeDistance(double value1, double value2) {
		if (value1 == 0 || value2 == 0) {
			return 0.0;
		}
		return 1 - Math.abs((value1 - value2) / (value1 + value2));
	}

	private double getTrackSimilarity(String trackId1, String trackId2) {
		double[] weight = { 0.25, 0.25, 0.25, 0.25 };
		double[] distances = new double[weight.length];

		Map<String, Object> trackFeatures1 = msdCache
				.getTrackFeatures(trackId1);
		Map<String, Object> trackFeatures2 = msdCache
				.getTrackFeatures(trackId2);

		distances[0] = getArtistSimilarity(
				(String) trackFeatures1.get("artist_id"),
				(String) trackFeatures2.get("artist_id"));
		distances[1] = -computeDistance(
				(double) trackFeatures1.get("loudness"),
				(double) trackFeatures2.get("loudness"));
		distances[2] = (double) trackFeatures2.get("song_hotttnesss");
		distances[3] = computeDistance((double) trackFeatures1.get("tempo"),
				(double) trackFeatures2.get("tempo"));

		double distance = 0;
		for (int i = 0; i < distances.length; i++) {
			distance += distances[i] * weight[i];
		}
		return -distance;
	}

	private void printRecommendations() {
		// Sorting in decreasing order of similarity
		Set<Entry<String, Double>> set = userRecommendations.entrySet();
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
			System.out.println(entry.getKey() + " : " + entry.getValue());
		}
	}

	private synchronized void updateRecommendations(
			Map<String, Double> recommendation) {
		userRecommendations.putAll(recommendation);
	}

	private class CompareUsers extends Thread {
		private final int index;
		private final ArrayList<String> currentUserTrackIds;
		private final ArrayList<String> similarUsers;

		public CompareUsers(ArrayList<String> currentUserTrackIds,
				ArrayList<String> similarUsers, int index) {
			this.currentUserTrackIds = currentUserTrackIds;
			this.similarUsers = similarUsers;
			this.index = index;
		}

		@Override
		public void run() {
			for (int i = 0; i < similarUsers.size(); i++) {
				if (i % Recommender.threadCount == index) {
					String similarUser = similarUsers.get(i);
					Map<String, Double> recommendation = getUserRecommendation(
							currentUserTrackIds, similarUser);
					updateRecommendations(recommendation);
				}
			}
		}
	}
}
