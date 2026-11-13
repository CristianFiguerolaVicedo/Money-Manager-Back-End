package com.cristian.moneymanager.repository;

import com.cristian.moneymanager.entity.ProfilePhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhotoEntity, Long> {

    Optional<ProfilePhotoEntity> findByPhotoKey(String photoKey);
}
