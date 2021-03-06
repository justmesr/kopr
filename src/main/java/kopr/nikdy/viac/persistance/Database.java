package kopr.nikdy.viac.persistance;

import kopr.nikdy.viac.entities.ParkingLot;
import kopr.nikdy.viac.entities.ParkingTicket;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Database {

    private static final String DATABASE_NAME = "parking_system";

    private static Connection connection;

    /**
     * Connects to the database and initializes all needed schemas, tables, indexes
     *
     * @throws ClassNotFoundException Driver not found
     * @throws SQLException           Error during table creation
     */
    public static void initialize() throws ClassNotFoundException, SQLException {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName("org.sqlite.JDBC");

        connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_NAME + ".db");
        initializeTables();
    }

    /**
     * Create parking_lot and parking_ticket tables if they do not exist already
     */
    private static void initializeTables() throws SQLException {
        Statement statement = connection.createStatement();

        statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS parking_lot(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "capacity INTEGER NOT NULL CHECK ( capacity >= 0 )," +
                        "name VARCHAR(256) UNIQUE NOT NULL" +
                        ");"
        );
        statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS parking_ticket(" +
                        "id BLOB(16) PRIMARY KEY," +
                        "car_licence_plate VARCHAR(16) NOT NULL," +
                        "parking_lot INTEGER REFERENCES parking_lot(id) NOT NULL," +
                        "arrival_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "leave_time DATETIME" +
                        ");"
        );
        statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS time_index ON parking_ticket(arrival_time, leave_time);"
        );
    }

    /**
     * Closes database connection if possible
     */
    public static void close() {
        try {
            if (connection != null) {
                connection.close();
            }

        } catch (SQLException e) {
            // connection close failed.
            System.err.println(e);
        }
    }

    /**
     * Saves parking lot to a database and sets it's ID
     *
     * @param parkingLot Parking lot to save to a database
     */
    public synchronized static void addParkingLot(ParkingLot parkingLot) throws SQLException {
        checkDatabaseInitialized();

        try (
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO parking_lot(name, capacity) VALUES (?, ?);",
                        Statement.RETURN_GENERATED_KEYS
                )
        ) {
            statement.setString(1, parkingLot.getName());
            statement.setInt(2, parkingLot.getCapacity());
            checkSomeRowsAffected(statement.executeUpdate());

            int generatedId = getGeneratedId(statement);
            parkingLot.setId(generatedId);
        }
    }

    /**
     * Saves ticket to a database
     *
     * @param ticket Ticket to save to a database
     */
    public synchronized static void addTicket(ParkingTicket ticket) throws SQLException, IOException {
        checkDatabaseInitialized();

        try (
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO parking_ticket(id, car_licence_plate, parking_lot, arrival_time) " +
                                "VALUES (?, ?, ?, ?);",
                        Statement.RETURN_GENERATED_KEYS
                )
        ) {
            statement.setBytes(1, Convert.toBytes(ticket.getId()));
            statement.setString(2, ticket.getCarLicencePlate());
            statement.setInt(3, ticket.getParkingLotId());
            statement.setTimestamp(4, Convert.toTimestamp(ticket.getArrivalTime()));

            checkSomeRowsAffected(statement.executeUpdate());
        }
    }

    /**
     * Mark ticket as used and set leave time as the time car with the ticket the left paring lot
     *
     * @param id Id of ticket that is removed. Removing means car with the ticket left parking lot and leave time is set
     */
    public synchronized static void removeTicket(UUID id) throws SQLException, IOException {
        checkDatabaseInitialized();

        try (
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE parking_ticket " +
                                "SET leave_time = ? " +
                                "WHERE id = ? AND " +
                                "leave_time IS NULL;"
                )
        ) {
            statement.setTimestamp(1, Convert.toTimestamp(LocalDateTime.now()));
            statement.setBytes(2, Convert.toBytes(id));

            checkSomeRowsAffected(statement.executeUpdate());
        }
    }

    /**
     * Get number of parking lot slots subtracted by number of tickets that weren't removed yet.
     *
     * @param id Id of parking lot to get remaining capacity
     * @return Number of free slots on the parking lot
     */
    public synchronized static int getParkingLotRemainingCapacity(Integer id) throws SQLException {
        checkDatabaseInitialized();

        try (
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT lot.capacity - COUNT(ticket.id) AS remaining " +
                                "FROM parking_lot AS lot " +
                                "LEFT JOIN parking_ticket AS ticket ON lot.id = ticket.parking_lot " +
                                "WHERE lot.id = ? AND ticket.leave_time IS NULL;"
                )
        ) {
            statement.setInt(1, id);
            return extractRemainingParkingLotCapacity(statement);
        }
    }


    /**
     * Execute statement and extract the result
     *
     * @param statement getParkingLotRemainingCapacity statement
     * @return Number of remaining slots, -1 if parking lot with such id doesn't exist
     */
    private static int extractRemainingParkingLotCapacity(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("remaining");
            }
        }

        return -1;
    }

    /**
     * Gets usage of selected Parking lots in percent. 100% being fully used, and 0% not used at all.
     *
     * @param ids IDs of parking lots to get usages of
     * @return Map of Parking lot Id -> usage
     */
    public synchronized static Map<Integer, Double> getUsagesInPercent(List<Integer> ids) throws SQLException {
        checkDatabaseInitialized();

        try (PreparedStatement statement = connection.prepareStatement(buildUsagesInPercentQuery(ids))) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setInt(i + 1, ids.get(i));
            }

            return getUsagesInPercentStatementResult(statement);
        }
    }

    /**
     * Build a statement, for querying parking lot usage with parameterized parking lot IDs
     */
    private static String buildUsagesInPercentQuery(List<Integer> ids) {
        return "SELECT lot.id AS id, COUNT(ticket.id) / CAST(lot.capacity AS REAL) * 100 AS percentage " +
                "FROM parking_lot AS lot " +
                "LEFT JOIN parking_ticket AS ticket ON ticket.parking_lot = lot.id " +
                "WHERE ticket.leave_time IS NULL " +
                "GROUP BY lot.id, lot.capacity " +
                "HAVING lot.id IN (" +
                ids.stream().map(value -> "?").collect(Collectors.joining(",")) +
                ");";
    }

    /**
     * Execute usagesInPercent statement and extract Selected info from it
     */
    private static Map<Integer, Double> getUsagesInPercentStatementResult(PreparedStatement statement) throws SQLException {
        Map<Integer, Double> result = new HashMap<>();

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int parkingLotId = resultSet.getInt("id");
                double usagePercentage = resultSet.getDouble("percentage");

                result.put(parkingLotId, usagePercentage);
            }
        }

        return result;
    }

    /**
     * Get number of tickets that were issued and removed on a particular parking lot
     *
     * @param id   ID of parking lot to get visitors of
     * @param date Day we want to count visitors at
     * @return Number of tickets that started and ended on the the day
     */
    public synchronized static int getParkingLotVisitorsDuringDay(Integer id, LocalDate date) throws SQLException {
        checkDatabaseInitialized();

        try (
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(ticket.id) AS count " +
                                "FROM parking_lot AS lot " +
                                "LEFT JOIN parking_ticket AS ticket ON ticket.parking_lot = lot.id " +
                                "WHERE lot.id = ? AND " +
                                "arrival_time >= ? AND " +
                                "leave_time IS NOT NULL AND " +
                                "leave_time <= ?;"
                )
        ) {
            int hoursInDay = 24;

            statement.setInt(1, id);
            statement.setTimestamp(2, Convert.toTimestamp(date.atStartOfDay()));
            statement.setTimestamp(3, Convert.toTimestamp(date.atStartOfDay().plusHours(hoursInDay)));

            return getParkingLotVisitorsStatementResult(statement);
        }
    }

    /**
     * Execute the parkingLotVisitorsDuringDay statement and extract the result from it
     *
     * @return count of visitors, -1 if parking lot with such id was not found
     */
    private static int getParkingLotVisitorsStatementResult(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("count");

            } else {
                return -1;
            }
        }
    }

    /**
     * Throw exception if database has not been properly initialized
     */
    private static void checkDatabaseInitialized() throws DatabaseNotInitializedException {
        if (connection == null) {
            throw new DatabaseNotInitializedException();
        }
    }

    /**
     * Throw exception if statement did not affect rows
     *
     * @param affectedRows Result of executed statement
     */
    private static void checkSomeRowsAffected(int affectedRows) throws SQLException {
        if (affectedRows == 0) {
            throw new SQLException("Statement failed, no rows affected.");
        }
    }

    /**
     * @param statement Executed statement
     * @return Generated ID of inserted row
     */
    private static int getGeneratedId(PreparedStatement statement) throws SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {

            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);

            } else {
                throw new SQLException("Insert failed, no ID obtained.");
            }
        }
    }

}
