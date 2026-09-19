package pl.jollycart.user;

import pl.jollycart.user.dto.UpdateUserRequest;
import pl.jollycart.user.dto.UserResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;


    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Użytkownik nie został znaleziony")
                );

        return UserResponse.from(user);
    }


    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Użytkownik nie został znaleziony")
                );

        return UserResponse.from(user);
    }


    public UserResponse updateUser(
            String currentEmail,
            UpdateUserRequest request
    ) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new RuntimeException("Użytkownik nie został znaleziony")
                );

        boolean nicknameChanged =
                !user.getNickname().equals(request.nickname());

        if (nicknameChanged &&
                userRepository.existsByNicknameAndIdNot(
                        request.nickname(),
                        user.getId()
                )) {

            throw new IllegalArgumentException(
                    "Nickname jest już zajęty"
            );
        }

        user.setNickname(request.nickname());
        user.setAvatarUrl(request.avatarUrl());

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}