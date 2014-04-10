package in.ac.iitk.cse.mti.aritra.amrs;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.umass.lastfm.Artist;
import de.umass.lastfm.Chart;
import de.umass.lastfm.User;

/**
 * Servlet implementation class Home
 */
@WebServlet("/home")
public class Home extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Home() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		String key = "d6a137eb39bc7831b26610a9d8885253";
		String user = "JRoar";
		Chart<Artist> chart = User.getWeeklyArtistChart(user, 10, key);
		DateFormat format = DateFormat.getDateInstance();
		String from = format.format(chart.getFrom());
		String to = format.format(chart.getTo());
		System.out.printf("Charts for %s for the week from %s to %s:%n", user, from, to);
		Collection<Artist> artists = chart.getEntries();
		for (Artist artist : artists) {
			System.out.println(artist.getName());
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
	}

}
