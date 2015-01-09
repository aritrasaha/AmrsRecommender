package in.ac.iitk.cse.mti.aritra.amrs;

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
 * 
 * @author aritra
 *
 */
@WebServlet("/home")
public class Home extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    private Connection conn;
    
    @Override
    public void init() throws ServletException {
        String dataLocation = File.separatorChar + "home" + File.separatorChar
                + "aritra" + File.separatorChar + "Development"
                + File.separatorChar + "data";
        
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
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest
     * , javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
    }

}
