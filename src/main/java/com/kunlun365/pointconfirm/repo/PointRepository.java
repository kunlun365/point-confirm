package com.kunlun365.pointconfirm.repo;

import com.kunlun365.pointconfirm.model.Point;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PointRepository {

    private final JdbcTemplate jdbcTemplate;

    public PointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Point> pointRowMapper = (rs, rowNum) -> {
        Point point = new Point();
        point.setSerialNo(rs.getInt("serial_no"));
        point.setCode(rs.getString("code"));
        point.setManagementMode(rs.getString("management_mode"));
        point.setManagementUnit(rs.getString("management_unit"));
        point.setDistrict(rs.getString("district"));
        point.setName(rs.getString("name"));
        point.setFormattedAddress(rs.getString("formatted_address"));
        point.setLng(rs.getDouble("lng"));
        point.setLat(rs.getDouble("lat"));
        point.setStatus(rs.getInt("status"));
        return point;
    };

    /**
     * 查询全部点位，按序号排序。
     */
    public List<Point> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM point_info ORDER BY serial_no",
                pointRowMapper
        );
    }

    /**
     * 查询指定序号的点位。
     */
    public Optional<Point> findBySerialNo(Integer serialNo) {
        List<Point> points = jdbcTemplate.query(
                "SELECT * FROM point_info WHERE serial_no = ?",
                pointRowMapper,
                serialNo
        );
        return points.stream().findFirst();
    }

    /**
     * 首次导入时插入点位。如果 serial_no 已存在，则跳过，不覆盖现有状态。
     */
    public int insertIfAbsent(Point point) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO point_info (" +
                        "serial_no, code, management_mode, management_unit, " +
                        "district, name, formatted_address, lng, lat, status" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                point.getSerialNo(),
                point.getCode(),
                point.getManagementMode(),
                point.getManagementUnit(),
                point.getDistrict(),
                point.getName(),
                point.getFormattedAddress(),
                point.getLng(),
                point.getLat()
        );
    }

    /**
     * 更新点位确认状态。返回受影响行数，1 表示更新成功。
     */
    public int updateStatus(Integer serialNo, int status) {
        return jdbcTemplate.update(
                "UPDATE point_info SET status = ? WHERE serial_no = ?",
                status,
                serialNo
        );
    }

    /**
     * 仅当点位仍处于待确认状态（status = 0）时更新。
     *
     * @return 1 表示更新成功；0 表示点位不存在或已被确认
     */
    public int updateStatusIfPending(Integer serialNo, int status) {
        return jdbcTemplate.update("UPDATE point_info SET status = ? WHERE serial_no = ? AND status = 0", status, serialNo);
    }
}
