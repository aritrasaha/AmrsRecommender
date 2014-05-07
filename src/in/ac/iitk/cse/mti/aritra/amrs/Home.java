package in.ac.iitk.cse.mti.aritra.amrs;

import in.ac.iitk.cse.mti.aritra.amrs.utils.MillionSongDataset;

import java.io.File;
import java.io.IOException;

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

	private final Recommender recommender;
	private final MillionSongDataset msd;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public Home() {
		super();
		String dataLocation = File.separatorChar + "home" + File.separatorChar
				+ "aritra" + File.separatorChar + "Development"
				+ File.separatorChar + "data";
		msd = new MillionSongDataset(dataLocation + File.separatorChar + "amrs"
				+ File.separatorChar + "MillionSong", "172.27.2.7");
		recommender = new Recommender(dataLocation, msd);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		recommender.recommend("RJ");
	}

}
