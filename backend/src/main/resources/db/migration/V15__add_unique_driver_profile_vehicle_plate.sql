-- FR-018: Không cho phép hai hồ sơ tài xế sử dụng cùng biển số xe.
-- PostgreSQL vẫn cho phép nhiều giá trị NULL với UNIQUE constraint.
ALTER TABLE driver_profile
    ADD CONSTRAINT uq_driver_profile_vehicle_plate UNIQUE (vehicle_plate);
