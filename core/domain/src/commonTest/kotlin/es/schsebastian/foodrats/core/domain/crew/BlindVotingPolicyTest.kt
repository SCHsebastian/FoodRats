package es.schsebastian.foodrats.core.domain.crew

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlindVotingPolicyTest {

    @Test fun masks_when_on_and_not_author_and_has_not_voted() =
        assertTrue(
            BlindVotingPolicy.shouldMaskAuthor(
                blindVoting = true,
                isAuthor = false,
                viewerHasVoted = false,
            ),
        )

    @Test fun never_masks_when_blind_voting_off() =
        assertFalse(
            BlindVotingPolicy.shouldMaskAuthor(
                blindVoting = false,
                isAuthor = false,
                viewerHasVoted = false,
            ),
        )

    @Test fun author_always_sees_own_meal() =
        assertFalse(
            BlindVotingPolicy.shouldMaskAuthor(
                blindVoting = true,
                isAuthor = true,
                viewerHasVoted = false,
            ),
        )

    @Test fun voting_reveals_the_author() =
        assertFalse(
            BlindVotingPolicy.shouldMaskAuthor(
                blindVoting = true,
                isAuthor = false,
                viewerHasVoted = true,
            ),
        )

    @Test fun author_who_voted_with_blind_off_still_unmasked() =
        assertFalse(
            BlindVotingPolicy.shouldMaskAuthor(
                blindVoting = false,
                isAuthor = true,
                viewerHasVoted = true,
            ),
        )
}
