package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    private static String[] checks = {"Password is at least 8 characters: ",
            "Password contains both uppercase and lowercase letters: ",
            "Password contains a mixture of letters and numbers: ",
            "Password includes at least one special character, from “!”, “@”, “#”, “?”: "};

    private static String[] patterns = {".{8,}", "\\b(?![a-z]+\\b|[A-Z]+\\b)[a-zA-Z]+", "[a-zA-Z][0-9]", "[!@#$?]+"};

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        // TODO: Part 1
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }

        //Check 3: Check if password fits requirements
        String[] pwhead = checkPWD(password, checks, patterns);
        if (pwhead[0].equals("f")) {
            System.out.println("Password did not fit requirements. Try Again!");
            System.out.println(pwhead[1]);
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        try {
            currentPatient = new Patient.PatientBuilder(username, salt, hash).build();
            currentPatient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }

        // Check 3: Check if password fits requirements
//        String[] pwhead = checkPWD(password, checks, patterns);
//        if (pwhead[0].equals("f")) {
//            System.out.println("Password did not fit requirements. Try Again!");
//            System.out.println(pwhead[1]);
//            return;
//        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            currentCaregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
            // save to caregiver information to our database
            currentCaregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // TODO: Part 1
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }

        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }

        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            obtainSchedule(d);
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace(System.out);
        }
    }

    private static void reserve(String[] tokens) {
        // TODO: Part 2
        // check 1: Make sure user logged in as a patient
        if (currentPatient == null) {
            System.out.println("Please login as a patient first!");
            return;
        }
        // Check 2: The length for tokens need to be exactly 2 tokens
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        String vaccine = tokens[2];
        try {
            Date d = Date.valueOf(date);
            reserveAppointment(d, vaccine);
        } catch (IllegalArgumentException e) {
            System.out.println("Please try again!");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error occurred when reserving appointment. Please try again!");
        }

    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            System.out.println("Please try again!");
        }
    }

    private static void cancel(String[] tokens) {
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        if (tokens.length != 1) {
            System.out.println("Please try again");
            return;
        }
        try {
            if (currentCaregiver != null) {
                showCaregiverAppointments(currentCaregiver.getUsername());
            } else if (currentPatient != null) {
                showPatientAppointments(currentPatient.getUsername());
            } else if (currentCaregiver == null && currentPatient != null) {
                System.out.println("Please login first!");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            System.out.println("Please try again!");
        }
    }

    private static void logout(String[] tokens) {
        if (tokens.length != 1) {
            System.out.println("Please try again");
            return;
        }
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }

        currentCaregiver = null;
        currentPatient = null;
        System.out.println("Successfully Logged out!");
        return;
    }

    public static void obtainSchedule(Date d) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getSchedule = "SELECT A.Username, V.Name, V.Doses FROM Availabilities as A, Vaccines as V WHERE A.Time = "+
                             "? ORDER BY A.username";
        try {
            PreparedStatement statement = con.prepareStatement(getSchedule);
            statement.setDate(1, d);
            ResultSet rs = statement.executeQuery();
            System.out.println("Available Caregivers on " + d + ":");
            if(!rs.isBeforeFirst()) {
                System.out.println("No Caregivers Available! Try another date!");
                return;
            }
            while(rs.next()){
                System.out.println(rs.getString("Username") + " " + rs.getString("Name")
                        + " " + rs.getString("Doses"));
            }
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public static void reserveAppointment(Date d, String vaccine) throws SQLException{
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String caregiver;
        int doses;
        int ID;

        String getAvailableInfo = "SELECT TOP 1 A.Username, (SELECT ISNULL((SELECT Doses FROM Vaccines WHERE Name = ?), 0)) as Doses, (SELECT ISNULL(MAX(App.ID), 0) FROM Appointments as App) as ID FROM Availabilities as A WHERE A.Time = ?";
        String updateTables = "INSERT INTO Appointments VALUES (? , ? , ? , ? , ?); DELETE FROM Availabilities WHERE Username = ? AND Time = ?; UPDATE Vaccines SET Doses = Doses-1 WHERE Name = ?";
        PreparedStatement statement;

        try {
            statement = con.prepareStatement(getAvailableInfo);
            statement.setString(1, vaccine);
            statement.setDate(2, d);
            ResultSet rs = statement.executeQuery();
            if(rs.next()) {
                caregiver = rs.getString("Username");
                doses = rs.getInt("Doses");
                ID = rs.getInt("ID") + 1;
            } else {
                System.out.println("No Caregiver Available for this date!");
                return;
            }
            if (doses == 0) {
                System.out.println("Not enough available doses of " + vaccine + " are available!");
                return;
            }
            statement = con.prepareStatement(updateTables);
            statement.setInt(1, ID);
            statement.setDate(2, d);
            statement.setString(3, vaccine);
            statement.setString(4, caregiver);
            statement.setString(5, currentPatient.getUsername());

            statement.setString(6, caregiver);
            statement.setDate(7, d);
            statement.setString(8, vaccine);

            statement.executeUpdate();
            System.out.println("Appointment ID: " + ID + " Caregiver Username: " + caregiver);
        }  catch (SQLException e) {
            System.out.print("Please try again!");
            e.printStackTrace();
            //throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public static void showCaregiverAppointments(String caregiver) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getAppointments = "SELECT ID, Vaccine_Name, Time, Patient_Name FROM Appointments WHERE Caregiver_Name = ? ORDER BY ID";
        try {
            PreparedStatement statement = con.prepareStatement(getAppointments);
            statement.setString(1, caregiver);
            ResultSet rs = statement.executeQuery();
            System.out.println("Appointments Scheduled for " + caregiver + ":");
            if(!rs.isBeforeFirst()) {
                System.out.println("No Appointments Scheduled!");
                return;
            }
            while(rs.next()){
                System.out.println(rs.getInt("ID") + " " +
                                   rs.getString("Vaccine_Name") + " " +
                                   rs.getDate("Time") + " " +
                                   rs.getString("Patient_Name"));
            }
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    public static void showPatientAppointments(String patient) throws SQLException{
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getAppointments = "SELECT ID, Vaccine_Name, Time, Caregiver_Name FROM Appointments WHERE Patient_Name = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getAppointments);
            statement.setString(1, patient);
            ResultSet rs = statement.executeQuery();
            System.out.println("Appointments Scheduled for " + patient + ":");
            if(!rs.isBeforeFirst()) {
                System.out.println("No Appointments Scheduled!");
                return;
            }
            while(rs.next()){
                System.out.println(rs.getInt("ID") + " " +
                        rs.getString("Vaccine_Name") + " " +
                        rs.getDate("Time") + " " +
                        rs.getString("Caregiver_Name"));
            }
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static String[] checkPWD(String password, String[] checks, String[] patterns) {
        Pattern pattern;
        String response = "";
        String[] header = new String[2];
        header[0] = "t";
        for (int i = 0; i < checks.length; i++) {
            response += checks[i];
            pattern = Pattern.compile(patterns[i]);
            if (pattern.matcher(password).find()) {
                response += "✔\n";
            } else {
                response += "❌\n";
                header[0] = "f";
            }
        }
        header[1] = response.trim();
        return header;
    }
}
