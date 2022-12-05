package com.alkemy.wallet.service;

import com.alkemy.wallet.dto.ResponseUserDto;
import com.alkemy.wallet.exception.ResourceFoundException;
import com.alkemy.wallet.exception.ResourceNotFoundException;
import com.alkemy.wallet.model.User;
import com.alkemy.wallet.repository.IUserRepository;
import com.alkemy.wallet.mapper.Mapper;
import com.alkemy.wallet.service.interfaces.ICustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements ICustomUserDetailsService {

    @Autowired
    private Mapper mapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

/*
    Agregar en las configuraciones de seguridad
    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }*/

    //    @Autowired
//    private IRoleService roleService;
//
//    @Autowired
//    private IAccountService accountService;
    @Override
    public ResponseUserDto save(@Valid ResponseUserDto responseUserDto) throws ResourceFoundException {  /*Acordar exceptions*/
        if (userRepository.existsByEmail(responseUserDto.getEmail())) {
            throw new ResourceFoundException("User email already exists");
        }

        User user = mapper.getMapper().map(responseUserDto, User.class);
        user.setPassword(passwordEncoder.encode(responseUserDto.getPassword()));


/*
 Agregar una vez que esten disponibles las entidades
*/
//        Role role = iRoleService.findByName(Role.ROLE_USER);
//        user.setRole(role); /*acomodar entidad Rol en User: un usuario tiene un rol*/

        User userSaved = userRepository.save(user);

//        accountService.addAccount(userSaved.getEmail(), Currency.USD));
//        accountService.addAccount(userSaved.getEmail(), Currency.ARS));

        ResponseUserDto userDto = mapper.getMapper().map(userSaved, ResponseUserDto.class);

        return userDto;

    }

    @Override
    public ResponseUserDto update(@Valid ResponseUserDto responseUserDto) throws ResourceNotFoundException {  /*Acordar exceptions*/
        if (!userRepository.existsByEmail(responseUserDto.getEmail())) {
            throw new ResourceNotFoundException("User email does not exists");
        }

        userRepository.findByEmail(responseUserDto.getEmail()).setUpdateDate(Date.valueOf(LocalDate.now()));
        userRepository.findByEmail(responseUserDto.getEmail()).setPassword(passwordEncoder.encode(responseUserDto.getPassword()));
        userRepository.findByEmail(responseUserDto.getEmail()).setFirstName(responseUserDto.getFirstName());
        userRepository.findByEmail(responseUserDto.getEmail()).setLastName(responseUserDto.getLastName());

        return responseUserDto;
    }

    @Override
    public ResponseUserDto findByEmail(String email) throws ResourceNotFoundException {
        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException("User not found");
        }
        ResponseUserDto userDto = new ResponseUserDto();
        userDto.setId(userRepository.findByEmail(email).getId());
        userDto.setFirstName(userRepository.findByEmail(email).getFirstName());
        userDto.setLastName(userRepository.findByEmail(email).getLastName());
        userDto.setEmail(userRepository.findByEmail(email).getEmail());
        userDto.setPassword(userRepository.findByEmail(email).getPassword());

        return userDto;
    }

    @Override
    public Boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    @Override
    public List<ResponseUserDto> findAll() {
        List<User> listaUser = userRepository.findAll();
        List<ResponseUserDto> listaResponse = new ArrayList<>();
        for (User user : listaUser) {
            ResponseUserDto responseUserDto = mapper.getMapper().map(user, ResponseUserDto.class);
            listaResponse.add(responseUserDto);
        }
        return listaResponse;
    }

    @Override
    public Page<ResponseUserDto> findAllPageable(Pageable pageable) {
        Page<User> listaUser = userRepository.findAll(pageable);
        return new PageImpl<ResponseUserDto>(
                findAll(), listaUser.getPageable(), listaUser.getTotalElements()
        );
    }
}
