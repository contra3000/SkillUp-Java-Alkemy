package com.alkemy.wallet.service;

import com.alkemy.wallet.dto.FixedTermDto;
import com.alkemy.wallet.dto.SimulatedFixedTermDto;
import com.alkemy.wallet.exception.FixedTermException;
import com.alkemy.wallet.mapper.Mapper;
import com.alkemy.wallet.model.Account;
import com.alkemy.wallet.model.FixedTermDeposit;
import com.alkemy.wallet.model.User;
import com.alkemy.wallet.repository.IAccountRepository;
import com.alkemy.wallet.repository.IFixedTermRepository;
import com.alkemy.wallet.repository.IUserRepository;
import com.alkemy.wallet.service.interfaces.IAccountService;
import com.alkemy.wallet.service.interfaces.IFixedTermService;
import com.alkemy.wallet.service.interfaces.IUserService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Hidden
@Service
public class FixedTermService implements IFixedTermService {

    private static final Integer MIN_DAYS = 30;
    private static final Double DAILY_INTEREST = 0.005;
    private final Mapper mapper;
    private final IUserService userService;
    private final IUserRepository userRepository;
    private final IFixedTermRepository fixedTermRepository;
    private final IAccountService accountService;
    private final IAccountRepository accountRepository;
    private final MessageSource messageSource;

    public FixedTermService(Mapper mapper, IUserService userService,
                            IUserRepository userRepository, IFixedTermRepository fixedTermRepository,
                            IAccountService accountService,
                            IAccountRepository accountRepository,
                            MessageSource messageSource) {
        this.mapper = mapper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.fixedTermRepository = fixedTermRepository;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.messageSource = messageSource;
    }

    @Override
    public FixedTermDto createFixedTerm(FixedTermDto fixedTermDto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        FixedTermDeposit fixedTerm = mapper.getMapper().map(fixedTermDto, FixedTermDeposit.class);

        Account account = accountRepository.findByCurrencyAndUser_Email(fixedTermDto.getCurrency(),
                user.getEmail());

        fixedTerm.setAccount(account);
        fixedTerm.setCreationDate(LocalDate.now());

        long days = ChronoUnit.DAYS.between(fixedTerm.getCreationDate(), fixedTerm.getClosingDate());

        if (days < MIN_DAYS) {
            throw new FixedTermException(messageSource.getMessage("fixedterm.exception",
                    new Object[]{MIN_DAYS}, Locale.ENGLISH));
        }

        fixedTerm.setInterest(fixedTerm.getAmount() * DAILY_INTEREST * days);
        accountService.updateBalance(account.getId(), fixedTerm.getAmount());
        FixedTermDeposit fixedTermSaved = fixedTermRepository.save(fixedTerm);
        FixedTermDto fixedTermDtoMapped = mapper.getMapper().map(fixedTermSaved, FixedTermDto.class);
        fixedTermDtoMapped.setCurrency(fixedTermSaved.getAccount().getCurrency());

        return fixedTermDtoMapped;

    }

    @Override
    public SimulatedFixedTermDto simulateFixedTerm(SimulatedFixedTermDto fixedTermDto) {
        long days = ChronoUnit.DAYS.between(fixedTermDto.getCreationDate(),
                fixedTermDto.getClosingDate());

        if (days < MIN_DAYS) {
            throw new FixedTermException(messageSource.getMessage("fixedterm.exception",
                    new Object[]{MIN_DAYS}, Locale.ENGLISH));
        }

        Double interest = fixedTermDto.getAmount() * DAILY_INTEREST * days;
        fixedTermDto.setInterest(interest);
        fixedTermDto.setTotalAmount(fixedTermDto.getAmount() + interest);

        return fixedTermDto;
    }
}
