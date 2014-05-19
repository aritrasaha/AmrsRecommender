package in.ac.iitk.cse.mti.aritra.amrs;

import in.ac.iitk.cse.mti.aritra.amrs.utils.MillionSongDataset;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class Home
 */
@WebServlet("/home")
public class Home extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private Recommender recommender;
	private MillionSongDataset msd;
	private Connection conn;

	@Override
	public void init() throws ServletException {
		String dataLocation = File.separatorChar + "home" + File.separatorChar
				+ "aritra" + File.separatorChar + "Development"
				+ File.separatorChar + "data";

		msd = new MillionSongDataset(dataLocation + File.separatorChar + "amrs"
				+ File.separatorChar + "MillionSong");

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

		recommender = new Recommender(dataLocation, msd, conn, 16);
	}

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public Home() {
		super();
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

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		recommender.recommend("3en", 50, 5, 10);
		recommender.recommend("RJ", 50, 5, 10);
		recommender.recommend("eartle", 50, 5, 10);
		recommender.recommend("franhale", 50, 5, 10);
		recommender.recommend("massdosage", 50, 5, 10);
	}

}
